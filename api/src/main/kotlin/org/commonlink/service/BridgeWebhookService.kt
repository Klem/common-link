package org.commonlink.service

import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Applies a Bridge payment-status notification to the corresponding payout.
 *
 * Bridge documents no webhook signature, so the notification is treated as a bare trigger: the
 * only thing taken from it is the payment-link id, and the authoritative state is then re-read
 * from Bridge with [BridgePaymentInitiationService.getPaymentLink]. Acting on the notification body
 * would let anyone who can reach the endpoint mark a payout settled — and settling a payout
 * publishes an on-chain attestation that cannot be retracted. Same rationale as
 * [MollieWebhookService].
 *
 * This is the sole driver of payout settlement: there is no polling loop. A Bridge notification is
 * retried for one to two days on a non-2xx response, which covers transient failures on our side.
 */
@Service
class BridgeWebhookService(
    private val payoutRepository: PayoutRepository,
    private val bridgeInitiation: BridgePaymentInitiationService,
    private val confirmer: PayoutConfirmer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Reconciles the payout attached to [paymentLinkId] with Bridge's current view of it.
     *
     * @param paymentLinkId Bridge payment-link id taken from the notification.
     * @throws org.commonlink.exception.BadGatewayException if Bridge cannot be reached — the caller
     *   must answer non-2xx so Bridge retries, rather than silently dropping the update.
     */
    fun handlePaymentLinkNotification(paymentLinkId: String) {
        val payout = payoutRepository.findByBridgePaymentLinkId(paymentLinkId)
        if (payout == null) {
            // Not an error: Bridge also notifies for payment links this instance never created
            // (another environment sharing the sandbox app, or a link created outside CommonLink).
            log.info("No payout attached to Bridge payment link {} — notification ignored", paymentLinkId)
            return
        }

        // Never trust the notification body: read the state from Bridge itself.
        val state = bridgeInitiation.getPaymentLink(paymentLinkId)

        when (state.status) {
            BridgePaymentStatus.ACSC ->
                confirmer.finaliseSettled(payout.id, state.transactionId)

            BridgePaymentStatus.RJCT ->
                confirmer.finaliseFailed(
                    payout.id,
                    state.statusReason ?: "Transfer rejected by the bank",
                    BridgePaymentStatus.RJCT,
                )

            BridgePaymentStatus.LINK_EXPIRED ->
                confirmer.finaliseFailed(
                    payout.id,
                    "Bank authorisation window expired before the transfer was authorised",
                    BridgePaymentStatus.LINK_EXPIRED,
                )

            BridgePaymentStatus.LINK_REVOKED ->
                confirmer.finaliseFailed(
                    payout.id,
                    "Payment link revoked before the transfer was authorised",
                    BridgePaymentStatus.LINK_REVOKED,
                )

            BridgePaymentStatus.PART -> {
                // A payout carries exactly one transaction, so a partial execution should be
                // impossible. Recorded and left engaged rather than guessed either way.
                log.error(
                    "Bridge reported PART on payment link {} for payout {} — a payout has a single " +
                        "transaction, so this needs manual reconciliation",
                    paymentLinkId, payout.id,
                )
                confirmer.recordInFlight(payout.id, BridgePaymentStatus.PART, state.transactionId)
            }

            BridgePaymentStatus.CREA,
            BridgePaymentStatus.ACTC,
            BridgePaymentStatus.PDNG ->
                confirmer.recordInFlight(payout.id, state.status, state.transactionId)
        }
    }
}

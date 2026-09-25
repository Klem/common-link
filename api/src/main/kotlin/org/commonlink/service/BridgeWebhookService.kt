package org.commonlink.service

import org.commonlink.config.BridgeProperties
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.PayoutErrorCode
import org.commonlink.entity.PayoutStatus
import org.commonlink.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Applies a Bridge payment-status notification to the corresponding payout.
 *
 * Bridge signs its webhooks (`BridgeApi-Signature`, verified by [BridgeWebhookSignatureVerifier]
 * before this class is ever reached), but the notification *content* is still treated as a bare
 * trigger: the only things taken from it are the payment-link id and, as a fallback, the client
 * reference, and the authoritative state is then re-read from Bridge with
 * [BridgePaymentInitiationService.getPaymentLink]. Acting on the notification body would let
 * anyone who can reach the endpoint mark a payout settled — and settling a payout publishes an
 * on-chain attestation that cannot be retracted. Same rationale as [MollieWebhookService], which
 * faces the same choice despite Mollie's webhook carrying no signature at all.
 *
 * This is the sole driver of payout settlement: there is no polling loop. A Bridge notification is
 * retried for one to two days on a non-2xx response, which covers transient failures on our side.
 */
@Service
class BridgeWebhookService(
    private val payoutRepository: PayoutRepository,
    private val bridgeInitiation: BridgePaymentInitiationService,
    private val confirmer: PayoutConfirmer,
    private val alerts: TechnicalAlertService,
    private val props: BridgeProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Reconciles the payout targeted by the notification with Bridge's current view of it.
     *
     * @param paymentLinkId Bridge payment-link id taken from the notification, when present.
     * @param clientReference `client_reference` from the notification — the payoutId as a string.
     *   Used only when [paymentLinkId] is absent, which Bridge documents as possible on
     *   `payment.transaction.created`/`.updated` (unlike `payment.link.updated`, where the link id
     *   is always present).
     * @param paymentRequestId `payment_request_id` from the notification, naming which of the
     *   link's payment requests moved. Passed through as an address, never as a state.
     * @throws org.commonlink.exception.BadGatewayException if Bridge cannot be reached — the caller
     *   must answer non-2xx so Bridge retries, rather than silently dropping the update.
     */
    fun handlePaymentLinkNotification(
        paymentLinkId: String?,
        clientReference: String? = null,
        paymentRequestId: String? = null,
    ) {
        // Projections, not entities: loading the Payout here would put it in the request-scoped
        // persistence context, and the confirmer's locked read would then be answered from the
        // identity map with the state as of *this* line — see [PayoutRepository.PayoutRouting].
        val payout = paymentLinkId?.takeIf { it.isNotBlank() }
            ?.let { payoutRepository.findRoutingByBridgePaymentLinkId(it) }
            ?: clientReference?.takeIf { it.isNotBlank() }
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?.let { payoutRepository.findRoutingById(it) }

        if (payout == null) {
            // Not an error: Bridge also notifies for payment links this instance never created
            // (another environment sharing the sandbox app, or a link created outside CommonLink).
            log.info(
                "No payout resolved for Bridge notification (paymentLinkId={}, clientReference={}) — ignored",
                paymentLinkId, clientReference,
            )
            return
        }

        val linkId = payout.bridgePaymentLinkId
        if (linkId == null) {
            log.error("Payout {} resolved via client_reference has no bridgePaymentLinkId recorded — cannot reconcile", payout.id)
            return
        }

        // Never trust the notification body: read the state from Bridge itself.
        val state = bridgeInitiation.getPaymentLink(linkId, paymentRequestId?.takeIf { it.isNotBlank() })

        when (state.status) {
            BridgePaymentStatus.ACSC -> {
                // The amount is reserved on the campaign only while the payout is PENDING with a
                // Bridge status on it. Settling once it has been **given back** — failed after a
                // rejection, or released when the link died — means the association may have
                // committed those funds elsewhere in between. The settlement is recorded all the
                // same, because the bank executed the transfer and refusing to record an observed
                // movement lies more gravely than correcting course; but it is not absorbed
                // silently.
                //
                // An already CONFIRMED payout is deliberately not that case, and testing it with
                // `status != PENDING` was the bug: its amount was never given back, it was spent
                // exactly as intended, and [PayoutConfirmer.finaliseSettled] is idempotent. Bridge
                // redelivers a settlement for up to two days and a notification for a superseded
                // link resolves here through the client_reference fallback, so that condition
                // raised the alarm on ordinary replays — observed 2026-09-24 at 08:58:45, forty
                // minutes after a correct settlement. An alert that cries wolf on every replay is
                // an alert nobody reads the day it means something.
                val amountWasGivenBack = payout.status == PayoutStatus.FAILED ||
                    (payout.status == PayoutStatus.PENDING && payout.bridgeStatus == null)
                if (amountWasGivenBack) {
                    log.error(
                        "Payout {} settled at Bridge while its amount was no longer engaged " +
                            "(status={}, bridgeStatus={}) — recording the settlement and alerting",
                        payout.id, payout.status, payout.bridgeStatus,
                    )
                    alerts.reportFailure(
                        TechnicalAlertKind.PAYOUT_SETTLED_AFTER_RELEASE,
                        "POST", "/api/public/webhooks/bridge", null,
                    )
                }
                confirmer.finaliseSettled(payout.id, state.transactionId)
            }

            BridgePaymentStatus.RJCT -> {
                // Revoke *before* failing, never after: failing returns the amount to the
                // campaign's confirmable balance, and Bridge leaves a rejected link usable — a
                // second authorisation on it would settle a transfer against funds already given
                // back, and publish an irretractable attestation for it. If the revocation cannot
                // be confirmed it throws, the payout stays engaged, and Bridge redelivers.
                //
                // Only a payout still PENDING is worth revoking for. A payout that has left
                // PENDING is one [PayoutConfirmer.finaliseFailed] refuses to move — already FAILED
                // (the link was revoked on the first delivery, and Bridge sends several
                // notifications per state change) or already CONFIRMED, where failing is refused
                // outright so the revocation would protect nothing the guard below does not
                // already refuse. It is not free either: a `5xx` on `POST /revoke` throws, the
                // webhook answers 502, and Bridge redelivers for two days over a call whose only
                // outcome was a no-op.
                if (payout.status == PayoutStatus.PENDING) {
                    bridgeInitiation.revokePaymentLink(linkId)
                }

                // Two of Bridge's own `status_reason` values are filed under RJCT without the bank
                // ever having refused anything: `NOAS` is a timeout waiting for the association,
                // `DS02` the association cancelling the order at its own bank. Failing those
                // stamps FAILED, which loadForConfirm refuses, retiring a payout for good because
                // someone let a screen expire — the distinction already drawn for a dead link, and
                // the same conclusion: a refusal is the bank being asked and saying no, an
                // abandonment is nobody ever asking, and only the first is terminal.
                //
                // The revocation above still applies to them. Bridge does not burn a link on a
                // RJCT — verified 2026-09-22, a rejected link still read `Valide` and a second
                // authorisation on it settled — so returning the amount while that URL lives is
                // exactly how 120 € left against funds already given back.
                val code = PayoutErrorCode.fromStatusReason(state.statusReason)
                if (code.isBankRefusal) {
                    confirmer.finaliseFailed(
                        payout.id,
                        state.statusReason ?: "Transfer rejected by the bank",
                        BridgePaymentStatus.RJCT,
                        code,
                    )
                } else {
                    confirmer.releaseReservation(
                        payout.id,
                        "Bridge reported $code — the transfer was never authorised",
                        code,
                    )
                }
            }

            // A dead link is not a refusal. Reached when no payment request exists, and also when
            // one exists that the association never authorised — see
            // [BridgePaymentStatus.survivesLinkDeath]: `CREA` and `ACTC` lose to a dead link,
            // because the URL that would let them be authorised no longer works. Either way
            // nothing can have been debited. Failing the payout would stamp FAILED, which
            // loadForConfirm refuses, retiring it for good because the association closed the tab.
            // It goes back to a retryable PENDING with its amount returned to the campaign
            // instead. releaseReservation refuses to touch a payout that already left PENDING, so
            // a link we revoked ourselves after a rejection cannot resurrect the payout it was
            // revoked for.
            BridgePaymentStatus.LINK_EXPIRED ->
                releaseUnlessJustMoved(
                    payout,
                    "Bank authorisation window expired before the transfer was authorised",
                    PayoutErrorCode.LINK_EXPIRED,
                )

            BridgePaymentStatus.LINK_REVOKED ->
                releaseUnlessJustMoved(
                    payout,
                    "Payment link revoked before the transfer was authorised",
                    PayoutErrorCode.LINK_REVOKED,
                )

            BridgePaymentStatus.PART -> {
                // A payout carries exactly one transaction, so a partial execution should be
                // impossible. Recorded and left engaged rather than guessed either way — and
                // alerted, because "left for manual reconciliation" is only true if somebody is
                // told. A log.error alone was a reconciliation nobody had been asked to do.
                log.error(
                    "Bridge reported PART on payment link {} for payout {} — a payout has a single " +
                        "transaction, so this needs manual reconciliation",
                    linkId, payout.id,
                )
                alerts.reportFailure(
                    TechnicalAlertKind.PAYOUT_PARTIALLY_EXECUTED,
                    "POST", "/api/public/webhooks/bridge", null,
                )
                confirmer.recordInFlight(payout.id, BridgePaymentStatus.PART, state.transactionId)
            }

            BridgePaymentStatus.CREA,
            BridgePaymentStatus.ACTC,
            BridgePaymentStatus.PDNG ->
                confirmer.recordInFlight(payout.id, state.status, state.transactionId)
        }
    }

    /**
     * Releases a payout whose link died, unless its Bridge state moved moments ago.
     *
     * The race this exists for: Bridge stamps `ACTC` the instant the payer enters the tunnel and
     * keeps it for the **whole** bank authentication — measured on 2026-09-24, `CREA` at 08:18:53,
     * `ACTC` at 08:18:55, terminal answer at 08:20:16. An association that starts authorising a
     * minute before `expired_date` is therefore still at `ACTC` when the link dies, and `ACTC`
     * loses to a dead link. Released, its amount goes back to the campaign's confirmable balance
     * — and then the bank executes the transfer anyway. That is `PAYOUT_SETTLED_AFTER_RELEASE`,
     * with the association free to have committed the returned balance in between: the same
     * amount spent twice, caught only by an e-mail.
     *
     * So a payout heard from less than [BridgeProperties.releaseGrace] ago means "somebody is
     * probably at their bank right now", and the amount stays engaged. Read on
     * [PayoutRepository.PayoutRouting.bridgeSyncedAt], which any reading pushes forward — a
     * reconciler sweep therefore defers a release because *we* asked Bridge a question rather than
     * because anything moved. Nothing is lost by waiting: an expiry has no deadline, whereas
     * releasing early is unrecoverable. The deferral is deliberately not written anywhere — no
     * `recordInFlight`, which would reset the very clock this reads and defer for ever. The payout
     * simply keeps ageing,
     * and two paths pick it up: Bridge redelivers `payment.link.updated` several times per state
     * change, and past that [BridgePayoutReconciler] re-reads anything engaged and stale — its
     * query takes `CREA` and `ACTC` too, so a deferred release is always collected.
     *
     * Only the *timing* is decided here. Whether the release is legitimate at all stays in
     * [PayoutConfirmer.releaseReservation], which still refuses any payout that left PENDING.
     */
    private fun releaseUnlessJustMoved(
        payout: PayoutRepository.PayoutRouting,
        reason: String,
        code: PayoutErrorCode,
    ) {
        val movedAt = payout.bridgeSyncedAt
        val grace = props.releaseGrace

        if (movedAt != null && !grace.isZero && movedAt.isAfter(Instant.now().minus(grace))) {
            log.info(
                "Payout {} not released on a dead link — its Bridge state moved at {}, less than {} ago, " +
                    "so the association may be authorising at its bank right now; left engaged",
                payout.id, movedAt, grace,
            )
            return
        }

        confirmer.releaseReservation(payout.id, reason, code)
    }
}

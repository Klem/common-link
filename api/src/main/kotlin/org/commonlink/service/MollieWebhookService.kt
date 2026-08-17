package org.commonlink.service

import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Processes Mollie payment webhook notifications.
 *
 * Always re-fetches the payment from Mollie (never trusts the webhook body).
 * Routes based on [MolliePaymentStatus]:
 * - [MolliePaymentStatus.PAID] → record the real payment method, then confirm the pending donation
 *   (synchronous, fast path)
 * - [MolliePaymentStatus.isFailed] → log and no-op (donation stays unconfirmed)
 * - [MolliePaymentStatus.isPending] → no-op (wait for next webhook)
 */
@Service
class MollieWebhookService(
    private val mollieClient: MollieClient,
    private val donationService: DonationService,
    private val donationRepository: DonationRepository,
    private val mollieConnectTokenManager: MollieConnectTokenManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handles a single Mollie webhook event for [molliePaymentId].
     *
     * The caller (controller) always responds 200 regardless of exceptions thrown here.
     */
    @Transactional
    fun handleWebhook(molliePaymentId: String) {
        val providerRef = "mollie:$molliePaymentId"
        val pending = donationRepository.findByProviderRef(providerRef)
        if (pending == null) {
            logger.error("Cannot resolve association token for webhook {} — no pending donation found", molliePaymentId)
            throw NotFoundException("No pending donation for payment $molliePaymentId")
        }
        val associationId = pending.campaign.association.id!!
        val assocToken = try {
            mollieConnectTokenManager.getValidAccessToken(associationId)
        } catch (e: IllegalStateException) {
            logger.error("Mollie connection BROKEN for association {} — webhook {} cannot be processed", associationId, molliePaymentId)
            throw e
        }
        val payment = mollieClient.getPayment(molliePaymentId, bearerToken = assocToken)

        when {
            payment.status.isConfirmed -> {
                logger.info("Mollie webhook PAID for {} (method={})", molliePaymentId, payment.method)
                // Persist the real payment method BEFORE confirming: confirmDonation publishes
                // DonationConfirmedEvent in AFTER_COMMIT, and the receipt PDF is rendered from the
                // committed row. Writing the method afterwards would race the flush and the receipt
                // would print "Non précisé" for a donation whose method is known.
                pending.paymentMethod = payment.method
                donationRepository.save(pending)
                donationService.recordPayment(providerRef, pending.donor.id!!, pending.campaign.id!!, pending.amount)
            }
            payment.status.isFailed -> {
                logger.info("Mollie webhook {} for {} — no-op", payment.status, molliePaymentId)
            }
            else -> {
                logger.debug("Mollie webhook {} for {} — still pending", payment.status, molliePaymentId)
            }
        }
    }
}

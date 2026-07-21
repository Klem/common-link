package org.commonlink.service

import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Processes Mollie payment webhook notifications.
 *
 * Always re-fetches the payment from Mollie (never trusts the webhook body).
 * Routes based on [MolliePaymentStatus]:
 * - [MolliePaymentStatus.PAID] → confirm the pending donation (synchronous, fast path)
 * - [MolliePaymentStatus.isFailed] → log and no-op (donation stays unconfirmed)
 * - [MolliePaymentStatus.isPending] → no-op (wait for next webhook)
 */
@Service
class MollieWebhookService(
    private val mollieClient: MollieClient,
    private val donationService: DonationService,
    private val donationRepository: DonationRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handles a single Mollie webhook event for [molliePaymentId].
     *
     * The caller (controller) always responds 200 regardless of exceptions thrown here.
     */
    @Transactional
    fun handleWebhook(molliePaymentId: String) {
        val payment = mollieClient.getPayment(molliePaymentId)
        val providerRef = "mollie:$molliePaymentId"

        when {
            payment.status.isConfirmed -> {
                logger.info("Mollie webhook PAID for {}", molliePaymentId)
                val (donorProfileId, campaignId, amount) = resolveParams(providerRef, payment)
                donationService.recordPayment(providerRef, donorProfileId, campaignId, amount)
            }
            payment.status.isFailed -> {
                logger.info("Mollie webhook {} for {} — no-op", payment.status, molliePaymentId)
            }
            else -> {
                logger.debug("Mollie webhook {} for {} — still pending", payment.status, molliePaymentId)
            }
        }
    }

    /**
     * Resolves donorProfileId, campaignId, and amount from the existing pending donation row,
     * falling back to Mollie payment metadata if the row does not exist (edge case).
     */
    private fun resolveParams(
        providerRef: String,
        payment: MolliePayment,
    ): Triple<UUID, UUID, java.math.BigDecimal> {
        val pending = donationRepository.findByProviderRef(providerRef)
        if (pending != null) {
            return Triple(pending.donor.id!!, pending.campaign.id!!, pending.amount)
        }

        logger.warn("No pending donation found for {} — falling back to Mollie metadata", providerRef)
        val donorProfileId = payment.metadata["donorProfileId"]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw NotFoundException("donorProfileId missing from Mollie metadata for $providerRef")
        val campaignId = payment.metadata["campaignId"]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw NotFoundException("campaignId missing from Mollie metadata for $providerRef")
        return Triple(donorProfileId, campaignId, payment.amount)
    }
}

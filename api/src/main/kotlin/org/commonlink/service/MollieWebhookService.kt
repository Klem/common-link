package org.commonlink.service

import jakarta.persistence.EntityManager
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Processes Mollie payment webhook notifications.
 *
 * Always re-fetches the payment from Mollie (never trusts the webhook body).
 * Routes based on [MolliePaymentStatus]:
 * - [MolliePaymentStatus.PAID] → check the amount ([PaymentAmountGuard]: a mismatch leaves the donation pending),
 *   record the real payment method, then confirm the pending donation dated at Mollie's `paidAt`
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
    private val paymentAmountGuard: PaymentAmountGuard,
    private val transactionTemplate: TransactionTemplate,
    private val entityManager: EntityManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handles a single Mollie webhook event for [molliePaymentId].
     *
     * Also reached from the return-page status poll ([org.commonlink.payment.MollieProviderAdapter.refreshPayment])
     * and from [MolliePaymentReconciler], so two calls for one payment may run at the same time. The token renewal
     * and the Mollie read run outside any transaction; the paid outcome is then applied in a short transaction under
     * the donation's row lock, on a refreshed instance ([applyPaid]). Without that, a second caller holding a stale,
     * unconfirmed copy passed the `confirmedAt` guards and credited the campaign twice (audit 2026-11-12, H4).
     *
     * The caller (controller) always responds 200 regardless of exceptions thrown here.
     *
     * @throws NotFoundException when no donation carries this payment.
     * @throws IllegalStateException when the association's Mollie connection is missing or BROKEN.
     */
    fun handleWebhook(molliePaymentId: String) {
        val providerRef = "mollie:$molliePaymentId"
        val associationId = donationRepository.findAssociationIdByProviderRef(providerRef)
        if (associationId == null) {
            logger.error("Cannot resolve association token for webhook {} — no pending donation found", molliePaymentId)
            throw NotFoundException("No pending donation for payment $molliePaymentId")
        }
        val assocToken = try {
            mollieConnectTokenManager.getValidAccessToken(associationId)
        } catch (e: IllegalStateException) {
            logger.error("Mollie connection BROKEN for association {} — webhook {} cannot be processed", associationId, molliePaymentId)
            throw e
        }
        val payment = mollieClient.getPayment(molliePaymentId, bearerToken = assocToken)

        when {
            payment.status.isConfirmed -> transactionTemplate.executeWithoutResult { applyPaid(providerRef, payment) }
            payment.status.isFailed -> {
                logger.info("Mollie webhook {} for {} — no-op", payment.status, molliePaymentId)
            }
            else -> {
                logger.debug("Mollie webhook {} for {} — still pending", payment.status, molliePaymentId)
            }
        }
    }

    /**
     * Confirms [providerRef]'s donation for a paid [payment]. Must run in a transaction.
     *
     * The donation is locked and refreshed first: under open-in-view the caller may already hold it, unlocked and
     * possibly stale, in the persistence context — the same rule as
     * [HelloAssoCheckoutService][org.commonlink.service.HelloAssoCheckoutService]'s confirmation path. Already
     * confirmed (resent webhook, return page, reconciler): nothing more to do. Wrong amount: left pending.
     */
    private fun applyPaid(providerRef: String, payment: MolliePayment) {
        val donation = donationRepository.findByProviderRefForUpdate(providerRef)
            ?: throw NotFoundException("No pending donation for payment ${payment.id}")
        entityManager.refresh(donation)
        if (donation.confirmedAt != null) {
            logger.info("Mollie payment {} already confirmed — no-op", payment.id)
            return
        }
        if (!paymentAmountGuard.matches(donation, payment.amount)) {
            logger.error("Mollie webhook PAID for {} with another amount: donation left pending", payment.id)
            return
        }
        logger.info("Mollie webhook PAID for {} (method={})", payment.id, payment.method)
        // Persist the real payment method BEFORE confirming: confirmDonation publishes
        // DonationConfirmedEvent in AFTER_COMMIT, and the receipt PDF is rendered from the
        // committed row. Writing the method afterwards would race the flush and the receipt
        // would print "Non précisé" for a donation whose method is known.
        donation.paymentMethod = payment.method
        donationRepository.save(donation)
        donationService.recordPayment(
            providerRef, donation.donor.id!!, donation.campaign.id!!, donation.amount, payment.paidAt,
        )
    }
}

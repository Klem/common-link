package org.commonlink.service

import org.commonlink.config.HelloAssoProperties
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Backstop for HelloAsso notifications (flow §6): re-reads `helloasso:` donations still pending
 * [HelloAssoProperties.reconcilerStaleAfter] after creation, until [HelloAssoProperties.checkoutAbandonAfter] — past
 * that, an unpaid checkout is abandoned and nothing is written (the cap releases its reservation on its own).
 *
 * Selected by `providerRef` prefix, never by the association's current provider. A donation this reconciler confirms
 * never got its notification through: [TechnicalAlertKind.MISSED_WEBHOOK] is raised and the link's
 * `webhook_missing_at` set, which shows the association a `WEBHOOK_MISSING` warning (its notification URL is probably
 * not configured in HelloAsso) until a notification arrives again.
 *
 * Disabled by `app.helloasso.reconciler.enabled=false`.
 */
@Service
@ConditionalOnProperty(prefix = "app.helloasso.reconciler", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class HelloAssoPaymentReconciler(
    private val donationRepository: DonationRepository,
    private val checkoutService: HelloAssoCheckoutService,
    private val technicalAlertService: TechnicalAlertService,
    private val properties: HelloAssoProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Re-reads every `helloasso:` donation created between [HelloAssoProperties.checkoutAbandonAfter] and
     * [HelloAssoProperties.reconcilerStaleAfter] ago and still unconfirmed (both bounds in the query); flags the link
     * of each one it confirms. A failed re-read is logged and retried at the next run.
     */
    @Scheduled(
        initialDelayString = "\${app.helloasso.reconciler.initial-delay-ms:150000}",
        fixedDelayString = "\${app.helloasso.reconciler.delay-ms:300000}",
    )
    fun reconcile() {
        val now = Instant.now()
        val prefix = PaymentProviderId.HELLOASSO.refPrefix
        val stale = donationRepository.findStalePendingByProviderRefPrefix(
            prefix,
            threshold = now.minus(properties.reconcilerStaleAfter),
            createdAfter = now.minus(properties.checkoutAbandonAfter),
        )
        if (stale.isEmpty()) {
            log.debug("HelloAsso reconciler: no donation to re-read")
            return
        }
        log.info("HelloAsso reconciler: {} donation(s) pending more than {}", stale.size, properties.reconcilerStaleAfter)
        for (donation in stale) {
            val outcome = runCatching { checkoutService.refreshPayment(donation.providerRef) }
                .onFailure { log.warn("HelloAsso reconciler could not re-read {}: {}", donation.providerRef, it.javaClass.simpleName) }
                .getOrNull()
            if (outcome != HelloAssoPaymentOutcome.CONFIRMED) continue
            log.warn("HelloAsso reconciler confirmed donation {}: its notification never confirmed it", donation.id)
            technicalAlertService.reportFailure(TechnicalAlertKind.MISSED_WEBHOOK, null, donation.providerRef, null)
            checkoutService.markWebhookMissing(donation.providerRef)
        }
    }
}

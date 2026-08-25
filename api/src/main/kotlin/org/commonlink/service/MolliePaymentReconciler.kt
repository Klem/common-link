package org.commonlink.service

import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Backstop for [MollieWebhookService]: periodically re-checks donations that are still pending
 * well after creation, in case the Mollie webhook that should have confirmed them was never
 * delivered (e.g. the configured `webhookUrl` was unreachable) or was delivered and its
 * processing failed.
 *
 * [GlobalExceptionHandler][org.commonlink.exception.GlobalExceptionHandler]-based alerting can
 * only report exceptions thrown while handling a request that reached the app — it has no way to
 * notice a callback that never arrived. This reconciler is the only mechanism that can, because it
 * doesn't wait for an inbound signal: it asks Mollie directly.
 *
 * Safe to run concurrently with a real webhook delivery: [MollieWebhookService.handleWebhook] and
 * [DonationService.recordPayment] are both idempotent on `providerRef`, so whichever path reaches
 * a donation first wins and the other is a no-op.
 *
 * Disabled by `app.mollie.reconciler.enabled=false`, same convention as
 * [MollieTokenRefreshScheduler].
 */
@Service
@ConditionalOnProperty(
    prefix = "app.mollie.reconciler",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class MolliePaymentReconciler(
    private val donationRepository: DonationRepository,
    private val mollieWebhookService: MollieWebhookService,
    private val technicalAlertService: TechnicalAlertService,
    @Value("\${app.mollie.reconciler.stale-after-minutes:15}") private val staleAfterMinutes: Long,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "\${app.mollie.reconciler.initial-delay-ms:120000}",
        fixedDelayString = "\${app.mollie.reconciler.delay-ms:300000}",
    )
    fun reconcile() {
        val threshold = Instant.now().minus(Duration.ofMinutes(staleAfterMinutes))
        val stale = donationRepository.findStalePending(threshold)
        if (stale.isEmpty()) return

        logger.info("Reconciler: {} donation(s) pending more than {} min", stale.size, staleAfterMinutes)
        stale.forEach { donation ->
            val paymentId = donation.providerRef.removePrefix("mollie:")
            if (paymentId == donation.providerRef) {
                // Not a Mollie payment (e.g. a future "stripe:" providerRef) — nothing this
                // reconciler knows how to check.
                return@forEach
            }
            runCatching { mollieWebhookService.handleWebhook(paymentId) }
                .onFailure {
                    logger.warn("Reconciler could not process {}: {}", donation.providerRef, it.javaClass.simpleName)
                }

            val confirmed = donationRepository.findById(donation.id!!).map { it.confirmedAt != null }.orElse(false)
            if (confirmed) {
                logger.warn(
                    "Reconciler confirmed donation {} — the webhook for {} never confirmed it on its own",
                    donation.id,
                    donation.providerRef,
                )
                technicalAlertService.reportFailure(
                    kind = TechnicalAlertKind.MISSED_WEBHOOK,
                    httpMethod = null,
                    path = donation.providerRef,
                    ex = null,
                )
            }
        }
    }
}

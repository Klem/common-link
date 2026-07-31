package org.commonlink.service

import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.event.MollieOnboardingStatusChangedEvent
import org.commonlink.repository.AssociationProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Sends a transition email whenever a [MollieOnboardingStatusChangedEvent] is published.
 *
 * Runs `@Async` so the email send does not block the HTTP response thread. Fetches association
 * data in its own async thread (new Hibernate session) to avoid lazy-load issues. Any failure
 * is caught and logged — a missed email must never bubble up to the API caller.
 *
 * Idempotence: the 5-minute throttle in [MollieConnectService] ensures at most one event per
 * status change per association per polling window. On restart, the persisted `lastSyncedAt`
 * prevents a re-poll within 5 minutes, so a given transition fires exactly once.
 */
@Component
class MollieConnectEmailListener(
    private val emailService: EmailService,
    private val associationRepo: AssociationProfileRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Async
    fun onStatusChanged(event: MollieOnboardingStatusChangedEvent) {
        val association = associationRepo.findById(event.associationId).orElse(null) ?: run {
            logger.warn("Association {} not found for KYC transition email", event.associationId)
            return
        }
        val email = association.contactEmail ?: run {
            logger.warn(
                "No contactEmail on association {} — skipping KYC transition email",
                event.associationId,
            )
            return
        }
        try {
            when (event.newStatus) {
                MollieOnboardingStatus.NEEDS_DATA ->
                    emailService.sendMollieOnboardingNeedsData(association.name, email)
                MollieOnboardingStatus.IN_REVIEW ->
                    emailService.sendMollieOnboardingInReview(association.name, email)
                MollieOnboardingStatus.COMPLETED ->
                    emailService.sendMollieOnboardingCompleted(association.name, email)
            }
            logger.info(
                "Sent Mollie KYC transition email ({} → {}) for association {}",
                event.previousStatus, event.newStatus, event.associationId,
            )
        } catch (ex: Exception) {
            logger.warn(
                "Failed to send Mollie KYC transition email for association {}: {}",
                event.associationId, ex.message,
            )
        }
    }
}

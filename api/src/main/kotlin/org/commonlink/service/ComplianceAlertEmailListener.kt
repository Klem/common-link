package org.commonlink.service

import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.event.ComplianceAlertOpenedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Notifies the compliance function by e-mail when an asset-freeze alert is raised on a donor.
 *
 * ### Why only the donation origin
 * Scoped to [ComplianceAlertOrigin.FREEZE_HIT_DONATION]. The onboarding origin already has a human
 * in the loop — a curator is sitting in front of the dossier when the screening runs and sees the
 * refusal immediately. A donation happens with nobody watching: the donor is bounced with a neutral
 * message, the association is never told, and without this e-mail the alert sits in `PENDING` until
 * somebody happens to open the back-office.
 *
 * **Widening the origin set is not a one-line change.** The message body asserts that the donation
 * was refused and that no payment was created — true for [ComplianceAlertOrigin.FREEZE_HIT_DONATION],
 * where the throw in `PublicWidgetService.createDonation` precedes any Mollie call, but false or
 * unverified elsewhere: [ComplianceAlertOrigin.SCREENING_UNAVAILABLE] is also raised from the
 * onboarding path, and [ComplianceAlertOrigin.ATYPICALITY_RULE] would by design fire on an *already
 * settled* donation. A compliance e-mail that misstates whether money moved is worse than no e-mail —
 * any new origin needs its own body text, not just an entry in the guard below.
 *
 * ### Delivery semantics
 * `AFTER_COMMIT` — [ComplianceAlertService.createOrIgnore] runs `REQUIRES_NEW`, so the alert row is
 * committed by the time this fires and no e-mail can announce an alert that was rolled back.
 * `@Async` keeps SMTP off the request thread: the caller of the screening is a public donation
 * endpoint and must not wait on a mail server. Any failure is caught and logged — a missed
 * notification must never turn into a 500 on the donation path, and must never mask the refusal.
 *
 * ### Recipient
 * A **function** mailbox, not a person: an asset-freeze alert must keep reaching the compliance
 * function across staff changes. Configured via `app.compliance.alert-notification-email`.
 *
 * ### Identity
 * Nothing identifying travels in the message — see [EmailService.sendDonorFreezeAlertOpened].
 */
@Component
class ComplianceAlertEmailListener(
    private val emailService: EmailService,
    @Value("\${app.compliance.alert-notification-email}") private val recipientEmail: String,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onAlertOpened(event: ComplianceAlertOpenedEvent) {
        if (event.origin != ComplianceAlertOrigin.FREEZE_HIT_DONATION) return

        if (recipientEmail.isBlank()) {
            logger.warn(
                "app.compliance.alert-notification-email is not configured — donor freeze alert {} raised with no notification sent",
                event.alertId,
            )
            return
        }

        val alertUrl = "${frontendUrl.trimEnd('/')}/fr/compliance/alerts/${event.alertId}"
        try {
            emailService.sendDonorFreezeAlertOpened(
                recipientEmail = recipientEmail,
                alertId = event.alertId,
                severity = event.severity.name,
                alertUrl = alertUrl,
            )
            logger.info("Notified compliance of donor freeze alert {}", event.alertId)
        } catch (e: Exception) {
            // Logged at ERROR, not WARN: a freeze alert that reaches nobody is a control failure,
            // not a cosmetic miss. The alert row itself is already committed and visible in the
            // back-office, so the information is not lost — only its push.
            logger.error(
                "Failed to notify compliance of donor freeze alert {}: {}",
                event.alertId, e.javaClass.simpleName,
            )
        }
    }
}

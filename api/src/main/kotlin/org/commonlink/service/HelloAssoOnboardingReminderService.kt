package org.commonlink.service

import org.commonlink.entity.HelloAssoConnection
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * "No HelloAsso account yet" journey (decision 2026-10-30): when the association starts it, CommonLink records the time
 * and reminds it once at D+1 and once at D+7 to paste its API key — never after its account is linked, nor once it
 * collects through another provider.
 */
@Service
class HelloAssoOnboardingReminderService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val connectionRepository: HelloAssoConnectionRepository,
    private val rows: HelloAssoConnectionRows,
    private val emailService: EmailService,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        val FIRST_REMINDER: Duration = Duration.ofDays(1)
        val LAST_REMINDER: Duration = Duration.ofDays(7)
    }

    /**
     * Records that the association started the journey (button « Commencer »). Idempotent: the first time is kept.
     *
     * @throws org.commonlink.exception.HelloAssoNotAssociationProviderException when the association is not on HELLOASSO.
     */
    @Transactional
    fun markStarted(userId: UUID) {
        val association = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        rows.requireHelloAssoProvider(association)
        val connection = rows.findOrCreate(association)
        if (connection.onboardingStartedAt != null) return
        connection.onboardingStartedAt = Instant.now()
        connection.updatedAt = Instant.now()
        connectionRepository.save(connection)
        log.info("HelloAsso journey started by association {}", association.id)
    }

    /**
     * Sends every due reminder; exits after a single query when none is pending. The D+7 reminder supersedes a missed
     * D+1 one (never two emails at once). Never throws: a failed email is retried at the next run.
     */
    fun sendDueReminders() {
        if (!connectionRepository.existsPendingHelloAssoReminder()) {
            log.debug("HelloAsso reminders: none pending")
            return
        }
        val now = Instant.now()
        for (connection in connectionRepository.findPendingHelloAssoReminders()) {
            val started = connection.onboardingStartedAt ?: continue
            val final = connection.reminderJ7SentAt == null && !started.plus(LAST_REMINDER).isAfter(now)
            val first = connection.reminderJ1SentAt == null && !started.plus(FIRST_REMINDER).isAfter(now)
            if (!final && !first) continue
            if (send(connection, final)) markSent(connection.association.id!!, final, now)
        }
    }

    private fun send(connection: HelloAssoConnection, final: Boolean): Boolean {
        val association = connection.association
        val email = association.contactEmail?.takeIf { it.isNotBlank() } ?: run {
            log.warn("No contactEmail on association {} — HelloAsso reminder not sent", association.id)
            return false
        }
        return try {
            emailService.sendHelloAssoOnboardingReminder(association.name, email, "$frontendUrl/fr/dashboard/association/profile?tab=bank", final)
            log.info("Sent HelloAsso {} reminder to association {}", if (final) "D+7" else "D+1", association.id)
            true
        } catch (ex: Exception) {
            log.warn("Failed to send HelloAsso reminder to association {}: {}", association.id, ex.message)
            false
        }
    }

    @Transactional
    fun markSent(associationId: UUID, final: Boolean, at: Instant) {
        val connection = connectionRepository.findByAssociationIdForUpdate(associationId) ?: return
        connection.reminderJ1SentAt = connection.reminderJ1SentAt ?: at
        if (final) connection.reminderJ7SentAt = at
        connection.updatedAt = at
        connectionRepository.save(connection)
    }
}

/** Thin scheduler of the HelloAsso journey reminders (hourly check). */
@Component
class HelloAssoOnboardingReminderScheduler(private val service: HelloAssoOnboardingReminderService) {

    @Scheduled(
        initialDelayString = "\${app.helloasso.reminder-initial-delay-ms:300000}",
        fixedDelayString = "\${app.helloasso.reminder-check-delay-ms:3600000}",
    )
    fun tick() = service.sendDueReminders()
}

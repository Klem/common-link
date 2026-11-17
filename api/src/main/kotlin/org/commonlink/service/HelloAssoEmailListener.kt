package org.commonlink.service

import org.commonlink.event.HelloAssoConnectionBrokenEvent
import org.commonlink.repository.AssociationProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * HelloAsso emails to the association. `AFTER_COMMIT` + `@Async`; every failure is swallowed with a WARN — a missed
 * email must never fail the sweep or request that triggered it.
 */
@Component
class HelloAssoEmailListener(
    private val emailService: EmailService,
    private val associationRepo: AssociationProfileRepository,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Tells the association its HelloAsso access is dead and how to restore collection. Sent once per breakage. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onConnectionBroken(event: HelloAssoConnectionBrokenEvent) {
        val association = associationRepo.findById(event.associationId).orElse(null) ?: run {
            logger.warn("Association {} not found for HelloAsso broken-link email", event.associationId)
            return
        }
        val email = association.contactEmail?.takeIf { it.isNotBlank() } ?: run {
            logger.warn("No contactEmail on association {} — cannot warn it that HelloAsso collection is down", event.associationId)
            return
        }
        try {
            emailService.sendHelloAssoConnectionBroken(association.name, email, collectionTabUrl())
            logger.info("Sent HelloAsso broken-link email for association {}", event.associationId)
        } catch (ex: Exception) {
            logger.warn("Failed to send HelloAsso broken-link email for association {}: {}", event.associationId, ex.message)
        }
    }

    private fun collectionTabUrl() = "$frontendUrl/fr/dashboard/association/profile?tab=bank"
}

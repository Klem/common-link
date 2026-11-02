package org.commonlink.service

import org.commonlink.event.PaymentProviderChangedEvent
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Emails the association when a curator moves it to another payment provider.
 *
 * `AFTER_COMMIT`: nothing is sent when the change is rolled back. `@Async`, and every failure is swallowed with a
 * WARN: a missed email must never fail the curator's request. The curator's reason is never sent.
 */
@Component
class PaymentProviderChangeEmailListener(
    private val emailService: EmailService,
    private val associationRepo: AssociationProfileRepository,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onProviderChanged(event: PaymentProviderChangedEvent) {
        val association = associationRepo.findById(event.associationId).orElse(null) ?: run {
            logger.warn("Association {} not found for payment-provider change email", event.associationId)
            return
        }
        val email = association.contactEmail?.takeIf { it.isNotBlank() } ?: run {
            logger.warn("No contactEmail on association {} — skipping payment-provider change email", event.associationId)
            return
        }
        try {
            emailService.sendPaymentProviderChanged(
                association.name,
                email,
                displayName(event.to),
                "$frontendUrl/fr/dashboard/association/profile?tab=bank",
            )
            logger.info("Sent payment-provider change email ({} → {}) for association {}", event.from, event.to, event.associationId)
        } catch (ex: Exception) {
            logger.warn("Failed to send payment-provider change email for association {}: {}", event.associationId, ex.message)
        }
    }

    private fun displayName(provider: PaymentProviderId): String = when (provider) {
        PaymentProviderId.MOLLIE -> "Mollie"
        PaymentProviderId.HELLOASSO -> "HelloAsso"
    }
}

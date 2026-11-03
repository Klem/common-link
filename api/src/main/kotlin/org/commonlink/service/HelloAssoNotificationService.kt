package org.commonlink.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.config.HelloAssoProperties
import org.commonlink.entity.Donation
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** What a received HelloAsso notification turned into. */
enum class HelloAssoNotificationOutcome {
    /** Unknown notification token: nothing read, nothing written. */
    UNKNOWN_TOKEN,

    /** Not about one of CommonLink's payments (memberships, ticketing, other forms…): ignored, nothing kept. */
    IGNORED,

    /** One of CommonLink's payments: its re-read was scheduled. */
    PAYMENT,

    /** An organization event (name, eligibility): an eligibility re-read was scheduled. */
    ORGANIZATION,
}

/**
 * Receives a HelloAsso notification for one association (flow §6). HelloAsso sends **every** event of the organization
 * to its URL, so the first step is filtering: only an `Order` / `Payment` matching one of our checkout-intents (by id or
 * `metadata.publicRef`) and `Organization` events go further. Anything else is ignored **without persisting nor logging
 * its body** — only the time of reception is recorded on the link.
 *
 * Never trusts the body for a state: a payment is re-read with `GET checkout-intents/{id}`, an organization with
 * `GET organizations/{slug}`, asynchronously so HelloAsso gets its 200 at once. Idempotent: HelloAsso resends until it
 * gets a 200 (up to 16 times), and the re-read confirms a donation at most once.
 */
@Service
class HelloAssoNotificationService(
    private val connectionRepository: HelloAssoConnectionRepository,
    private val donationRepository: DonationRepository,
    private val objectMapper: ObjectMapper,
    private val processor: HelloAssoNotificationProcessor,
    private val properties: HelloAssoProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Whether [ip] is one of HelloAsso's notification source addresses. */
    fun isAllowedSource(ip: String): Boolean = ip in properties.allowedNotificationIps

    /** Filters [rawBody] received on [token] and schedules what concerns CommonLink. Never logs the body. */
    @Transactional
    fun receive(token: String, rawBody: String): HelloAssoNotificationOutcome {
        val connection = connectionRepository.findByWebhookToken(token) ?: run {
            log.warn("HelloAsso notification on an unknown token: ignored")
            return HelloAssoNotificationOutcome.UNKNOWN_TOKEN
        }
        val associationId = connection.association.id!!
        connection.webhookLastReceivedAt = Instant.now()
        connectionRepository.save(connection)

        val root = try {
            objectMapper.readTree(rawBody)
        } catch (ex: Exception) {
            log.warn("Unreadable HelloAsso notification for association {}: ignored", associationId)
            return HelloAssoNotificationOutcome.IGNORED
        }
        val eventType = root.path("eventType").asText("")
        if (eventType.startsWith("Organization")) {
            processor.refreshEligibility(associationId)
            log.info("HelloAsso {} notification for association {}: eligibility re-read scheduled", eventType, associationId)
            return HelloAssoNotificationOutcome.ORGANIZATION
        }
        val donation = when (eventType) {
            "Order" -> ourDonationByCheckoutIntent(root.path("data").path("checkoutIntentId"), connection)
            "Payment" -> ourDonationByPublicRef(root.path("metadata").path("publicRef"), connection)
            else -> null
        } ?: run {
            log.debug("HelloAsso notification for association {} is not one of ours: ignored", associationId)
            return HelloAssoNotificationOutcome.IGNORED
        }
        processor.refreshPayment(donation.providerRef)
        log.info("HelloAsso {} notification for donation {}: re-read scheduled", eventType, donation.id)
        return HelloAssoNotificationOutcome.PAYMENT
    }

    private fun ourDonationByCheckoutIntent(id: JsonNode, connection: HelloAssoConnection): Donation? {
        if (!id.canConvertToLong()) return null
        return donationRepository.findByProviderRef(PaymentProviderId.HELLOASSO.refPrefix + id.asLong())
            ?.takeIf { belongsTo(it, connection) }
    }

    private fun ourDonationByPublicRef(publicRef: JsonNode, connection: HelloAssoConnection): Donation? {
        val ref = runCatching { UUID.fromString(publicRef.asText()) }.getOrNull() ?: return null
        return donationRepository.findByPublicRef(ref)
            ?.takeIf { it.providerRef.startsWith(PaymentProviderId.HELLOASSO.refPrefix) && belongsTo(it, connection) }
    }

    private fun belongsTo(donation: Donation, connection: HelloAssoConnection) =
        donation.campaign.association.id == connection.association.id
}

/**
 * Asynchronous follow-up of a HelloAsso notification, after the 200 is sent. Failures are reported, never thrown back.
 */
@Component
class HelloAssoNotificationProcessor(
    private val checkoutService: ObjectProvider<HelloAssoCheckoutService>,
    private val eligibilityService: ObjectProvider<HelloAssoEligibilityService>,
    private val technicalAlertService: ObjectProvider<TechnicalAlertService>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Re-reads the payment at HelloAsso and applies its state (idempotent). */
    @Async
    fun refreshPayment(providerRef: String) {
        try {
            checkoutService.getObject().refreshPayment(providerRef)
        } catch (ex: Exception) {
            log.warn("HelloAsso payment re-read failed for {}: {}", providerRef, ex.javaClass.simpleName)
            technicalAlertService.ifAvailable?.reportFailure(
                TechnicalAlertKind.WEBHOOK_PROCESSING_FAILURE, "POST", "/api/public/webhooks/helloasso", ex,
            )
        }
    }

    /** Re-reads the organization's eligibility (never the notification's content). */
    @Async
    fun refreshEligibility(associationId: UUID) {
        eligibilityService.getObject().refresh(associationId)
    }
}

package org.commonlink.payment

import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.service.HelloAssoCheckoutService
import org.commonlink.service.HelloAssoEligibilityService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * [PaymentProvider] for HelloAsso (API keys mode). Status reads come from the database only (flow §10.5); checkout
 * creation and payment re-reads go through [HelloAssoCheckoutService].
 *
 * Derived link states: COLLECT_READY = ACTIVE and can collect; LINKED_PENDING = ACTIVE, cannot collect, never could;
 * SUSPENDED = ACTIVE, cannot collect, could before.
 */
@Component
class HelloAssoProviderAdapter(
    private val connectionRepository: HelloAssoConnectionRepository,
    private val checkoutService: ObjectProvider<HelloAssoCheckoutService>,
    private val eligibilityService: ObjectProvider<HelloAssoEligibilityService>,
) : PaymentProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val id: PaymentProviderId = PaymentProviderId.HELLOASSO

    companion object {
        /** Warning of the neutral status when HelloAsso notifications are not received (flow §10.5). */
        const val WEBHOOK_MISSING = "WEBHOOK_MISSING"

        /** HelloAsso back-office page where an association completes its verification (v2 mockup). */
        fun verificationUrl(slug: String) = "https://admin.helloasso.com/$slug/verification"
    }

    override fun readiness(associationId: UUID): ProviderReadiness {
        val connection = connectionRepository.findByAssociationId(associationId)
        return when (connection?.state) {
            null, HelloAssoConnectionState.NOT_LINKED, HelloAssoConnectionState.DISCONNECTED ->
                ProviderReadiness(ProviderReadinessStatus.NOT_CONNECTED)
            HelloAssoConnectionState.BROKEN ->
                ProviderReadiness(ProviderReadinessStatus.BROKEN, reason = "HelloAsso refresh token rejected")
            HelloAssoConnectionState.ACTIVE ->
                if (connection.canCollect) ProviderReadiness(ProviderReadinessStatus.READY)
                else ProviderReadiness(
                    ProviderReadinessStatus.PENDING,
                    reason = if (connection.everCompliant) "suspended by HelloAsso" else "awaiting HelloAsso verification",
                    actionUrl = connection.organizationSlug?.let(::verificationUrl),
                )
        }
    }

    override fun paymentStatus(associationId: UUID): PaymentStatus {
        val connection = connectionRepository.findByAssociationId(associationId)
        val warnings = if (connection != null && HelloAssoWebhookState.of(connection) == HelloAssoWebhookState.MISSING) {
            listOf(WEBHOOK_MISSING)
        } else emptyList()
        return when (connection?.state) {
            null, HelloAssoConnectionState.NOT_LINKED, HelloAssoConnectionState.DISCONNECTED -> PaymentStatus(BankSetupStatus.NOT_CONNECTED)
            HelloAssoConnectionState.BROKEN -> PaymentStatus(BankSetupStatus.BROKEN, warnings = warnings)
            HelloAssoConnectionState.ACTIVE -> when {
                connection.canCollect -> PaymentStatus(BankSetupStatus.COMPLETED, warnings = warnings)
                connection.everCompliant -> PaymentStatus(
                    BankSetupStatus.NEEDS_DATA,
                    actionUrl = connection.organizationSlug?.let(::verificationUrl),
                    warnings = warnings,
                )
                else -> PaymentStatus(BankSetupStatus.IN_REVIEW, warnings = warnings)
            }
        }
    }

    /** Throttled eligibility re-read when the association comes back to its page while it cannot collect. Never throws. */
    override fun refreshStatusIfStale(associationId: UUID) {
        try {
            eligibilityService.getObject().refreshIfStale(associationId)
        } catch (ex: Exception) {
            log.warn("HelloAsso status refresh failed for association {}: {}", associationId, ex.javaClass.simpleName)
        }
    }

    /** HelloAsso captures no contact data of CommonLink's at linking: nothing to lock (flow §10.1). */
    override fun isLinkStarted(associationId: UUID): Boolean = false

    override fun createCheckout(request: CheckoutRequest): CheckoutSession = checkoutService.getObject().createCheckout(request)

    override fun refreshPayment(providerRef: String) {
        require(providerRef.startsWith(id.refPrefix)) { "Not a HelloAsso payment reference" }
        checkoutService.getObject().refreshPayment(providerRef)
    }
}

/** Whether HelloAsso notifications reach CommonLink for a link (flow §10.5, brief §2.2). */
enum class HelloAssoWebhookState {
    /** Nothing received and nothing missed yet. */
    UNKNOWN,

    /** A notification was received (and none was missed since). */
    RECEIVED,

    /** The reconciler confirmed a payment whose notification never arrived, and none was received since. */
    MISSING;

    companion object {
        fun of(connection: HelloAssoConnection): HelloAssoWebhookState {
            val missing = connection.webhookMissingAt
            val received = connection.webhookLastReceivedAt
            return when {
                missing != null && (received == null || missing.isAfter(received)) -> MISSING
                received != null -> RECEIVED
                else -> UNKNOWN
            }
        }
    }
}

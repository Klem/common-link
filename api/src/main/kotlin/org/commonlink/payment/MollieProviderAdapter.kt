package org.commonlink.payment

import org.commonlink.config.MollieProperties
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.MolliePaymentException
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieOAuthStateRepository
import org.commonlink.service.MollieClient
import org.commonlink.service.MollieConnectService
import org.commonlink.service.MollieConnectTokenManager
import org.commonlink.service.MollieWebhookService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

/**
 * [PaymentProvider] backed by Mollie Connect. Pure delegation to the existing Mollie services: it adds no Mollie
 * logic of its own, it only moves behind the port what the donation flow and the collection gates did inline.
 *
 * [MollieConnectService] is resolved lazily through an [ObjectProvider]: it depends on `OnboardingGateService`,
 * which depends on [PaymentProviderRegistry], which collects this adapter — a direct injection would be a bean cycle.
 */
@Component
class MollieProviderAdapter(
    private val mollieConnectionRepository: MollieConnectionRepository,
    private val mollieOAuthStateRepository: MollieOAuthStateRepository,
    private val mollieConnectTokenManager: MollieConnectTokenManager,
    private val mollieClient: MollieClient,
    private val mollieProperties: MollieProperties,
    private val mollieWebhookService: MollieWebhookService,
    private val mollieConnectService: ObjectProvider<MollieConnectService>,
) : PaymentProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val id: PaymentProviderId = PaymentProviderId.MOLLIE

    /**
     * Reads the association's [org.commonlink.entity.MollieConnection], in the same order as the campaign
     * publication gate: no connection → [ProviderReadinessStatus.NOT_CONNECTED]; broken link →
     * [ProviderReadinessStatus.BROKEN]; [org.commonlink.entity.MollieConnection.canCollectDonations] false →
     * [ProviderReadinessStatus.PENDING], with Mollie's onboarding wizard link as `actionUrl` when Mollie returned
     * one; otherwise [ProviderReadinessStatus.READY]. Database only, no Mollie call.
     */
    override fun readiness(associationId: UUID): ProviderReadiness {
        val connection = mollieConnectionRepository.findByAssociationId(associationId)
            ?: return ProviderReadiness(ProviderReadinessStatus.NOT_CONNECTED)
        if (connection.state == MollieConnectionState.BROKEN) {
            return ProviderReadiness(ProviderReadinessStatus.BROKEN, reason = "Mollie connection is BROKEN")
        }
        if (!connection.canCollectDonations()) {
            return ProviderReadiness(
                ProviderReadinessStatus.PENDING,
                reason = "onboardingStatus=${connection.onboardingStatus.name} " +
                    "canReceivePayments=${connection.canReceivePayments}",
                actionUrl = connection.onboardingDashboardUrl,
            )
        }
        return ProviderReadiness(ProviderReadinessStatus.READY)
    }

    /**
     * Exact server-side translation of `deriveBankSetupStatus` (`app/src/lib/bankSetupStatus.ts`), fed with the
     * same stored data as `GET /api/mollie/connect/status`: a broken link wins over everything (even a stale
     * COMPLETED), then absence of a connection, then Mollie's own onboarding status. COMPLETED does not look at
     * `canReceivePayments`, exactly like the frontend. `actionUrl` is Mollie's onboarding wizard link, exposed
     * whenever a connection exists, like the status DTO's `dashboardUrl`.
     *
     * Unlike that endpoint, never re-fetches a stale status from Mollie: database only (see [refreshStatusIfStale]).
     */
    override fun paymentStatus(associationId: UUID): PaymentStatus {
        val connection = mollieConnectionRepository.findByAssociationId(associationId)
            ?: return PaymentStatus(BankSetupStatus.NOT_CONNECTED)
        val status = if (connection.state == MollieConnectionState.BROKEN) {
            BankSetupStatus.BROKEN
        } else {
            when (connection.onboardingStatus) {
                MollieOnboardingStatus.NEEDS_DATA -> BankSetupStatus.NEEDS_DATA
                MollieOnboardingStatus.IN_REVIEW -> BankSetupStatus.IN_REVIEW
                MollieOnboardingStatus.COMPLETED -> BankSetupStatus.COMPLETED
            }
        }
        return PaymentStatus(status, actionUrl = connection.onboardingDashboardUrl)
    }

    /**
     * Delegates to [MollieConnectService.refreshOnboardingStatusIfStale], the refresh `GET /api/mollie/connect/status`
     * performs on read (skipped when the sync is disabled, the connection is broken or already collecting, or was
     * synced less than 5 minutes ago). Any failure is logged and swallowed, as the port requires.
     */
    override fun refreshStatusIfStale(associationId: UUID) {
        try {
            mollieConnectService.getObject().refreshOnboardingStatusIfStale(associationId)
        } catch (ex: Exception) {
            logger.warn("Mollie status refresh failed for association {}: {}", associationId, ex.message)
        }
    }

    /**
     * True once the Mollie KYC flow has been initiated: a [org.commonlink.entity.MollieConnection] exists (the
     * OAuth callback ran), or a non-expired [org.commonlink.entity.MollieOAuthState] does (the client-link was
     * created, which already submitted the contact identity to Mollie). Backs
     * `OnboardingGateService.isPaymentLinkStarted`.
     */
    override fun isLinkStarted(associationId: UUID): Boolean {
        if (mollieConnectionRepository.findByAssociationId(associationId) != null) return true
        return mollieOAuthStateRepository.existsByAssociationIdAndExpiresAtAfter(associationId, Instant.now())
    }

    /**
     * Creates a Mollie hosted-checkout payment on the association's Connect account.
     *
     * Builds the return URLs (`/{locale}/embed/donate/{widgetToken}/return`, `?ref=` on success,
     * `?cancelled=true` on cancel, `&source=` when the source site is known), the description, the metadata and the
     * idempotency key (the request's `publicRef`).
     *
     * @throws ConflictException when the association has no Mollie connection, cannot collect yet, or its link is
     *   broken.
     * @throws MolliePaymentException when Mollie fails or returns no checkout URL — raised before any donation is
     *   recorded by the caller.
     */
    override fun createCheckout(request: CheckoutRequest): CheckoutSession {
        val assocToken = resolveMollieToken(request.associationId)
        val assocProfileId = mollieClient.getFirstProfileId(assocToken)

        val encodedSource = request.sourceSite?.let { URLEncoder.encode(it, StandardCharsets.UTF_8) }

        // Must be derived from something already unique per call and reused verbatim in every
        // Mollie-bound field (redirectUrl embeds publicRef below) — Mollie rejects a reused
        // Idempotency-Key whose request body differs from the first call with that key. A key
        // derived only from widget+donor+amount (independent of publicRef) let two genuinely
        // separate donations of the same amount within the same hour collide and 400.
        val idempotencyKey = request.publicRef.toString()

        val base = "${mollieProperties.redirectBaseUrl}/${request.locale}/embed/donate/${request.widgetToken}/return"
        // Only the opaque ref rides on the success redirect: /return polls the status by it, and the
        // `purchase` values are read from the server later (getDonationStatus), never from a URL a
        // donor could edit. The cancelled path needs no ref at all.
        val trackingQuery = "ref=${request.publicRef}"
        val redirectUrl = if (encodedSource != null) "$base?$trackingQuery&source=$encodedSource" else "$base?$trackingQuery"
        val cancelUrl = if (encodedSource != null) "$base?cancelled=true&source=$encodedSource" else "$base?cancelled=true"
        val description = "Don - ${request.campaignName}".take(255)

        logger.info(
            "Creating Mollie payment for widget={} campaign={} amount={}",
            request.widgetToken, request.campaignId, request.amount
        )

        val molliePayment = mollieClient.createPayment(
            amount = request.amount,
            currency = "EUR",
            description = description,
            redirectUrl = redirectUrl,
            cancelUrl = cancelUrl,
            webhookUrl = mollieProperties.webhookUrl,
            metadata = mapOf(
                "campaignId" to request.campaignId.toString(),
                "donorProfileId" to request.donorProfileId.toString(),
                "widgetToken" to request.widgetToken,
            ),
            idempotencyKey = idempotencyKey,
            bearerToken = assocToken,
            profileId = assocProfileId,
        )

        val checkoutUrl = molliePayment.checkoutUrl
            ?: throw MolliePaymentException("Mollie returned no checkout URL for payment ${molliePayment.id}")

        return CheckoutSession(
            providerRef = "${id.refPrefix}${molliePayment.id}",
            redirectUrl = checkoutUrl,
            providerPaymentId = molliePayment.id,
        )
    }

    /**
     * Re-reads the payment at Mollie and confirms the donation if paid, through
     * [MollieWebhookService.handleWebhook] — the same path as the webhook, so whichever runs first wins and the
     * other is a no-op.
     *
     * @throws IllegalArgumentException when [providerRef] is not a `mollie:` reference.
     */
    override fun refreshPayment(providerRef: String) {
        require(providerRef.startsWith(id.refPrefix)) { "Not a Mollie payment reference: $providerRef" }
        mollieWebhookService.handleWebhook(providerRef.removePrefix(id.refPrefix))
    }

    /**
     * Returns a valid Connect access token for the association, refusing with a 409 when it cannot collect.
     * Same predicate as the publish and widget-enable gates — see
     * [org.commonlink.entity.MollieConnection.canCollectDonations].
     */
    private fun resolveMollieToken(associationId: UUID): String {
        val connection = mollieConnectionRepository.findByAssociationId(associationId)
            ?: throw ConflictException("L'association n'a pas de connexion Mollie")
        if (!connection.canCollectDonations())
            throw ConflictException("L'association ne peut pas encore recevoir de paiements")
        return try {
            mollieConnectTokenManager.getValidAccessToken(associationId)
        } catch (e: IllegalStateException) {
            throw ConflictException("Connexion Mollie de l'association interrompue")
        }
    }
}

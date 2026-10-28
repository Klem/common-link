package org.commonlink.payment

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.commonlink.config.MollieProperties
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.MolliePaymentException
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieOAuthStateRepository
import org.commonlink.service.MollieClient
import org.commonlink.service.MollieConnectTokenManager
import org.commonlink.service.MolliePayment
import org.commonlink.service.MolliePaymentStatus
import org.commonlink.service.MollieWebhookService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Pure unit tests of [MollieProviderAdapter]: every Mollie collaborator is mocked. */
class MollieProviderAdapterTest {

    private val connectionRepository = mockk<MollieConnectionRepository>()
    private val oauthStateRepository = mockk<MollieOAuthStateRepository>()
    private val tokenManager = mockk<MollieConnectTokenManager>()
    private val mollieClient = mockk<MollieClient>()
    private val webhookService = mockk<MollieWebhookService>()
    private val properties = MollieProperties(
        redirectBaseUrl = "https://app.example",
        webhookUrl = "https://api.example/api/webhooks/mollie",
    )

    private val adapter = MollieProviderAdapter(
        connectionRepository, oauthStateRepository, tokenManager, mollieClient, properties, webhookService,
    )

    private val associationId = UUID.randomUUID()

    private fun connection(
        state: MollieConnectionState = MollieConnectionState.ACTIVE,
        onboardingStatus: MollieOnboardingStatus = MollieOnboardingStatus.COMPLETED,
        canReceivePayments: Boolean = true,
        dashboardUrl: String? = null,
    ): MollieConnection = mockk {
        every { this@mockk.state } returns state
        every { this@mockk.onboardingStatus } returns onboardingStatus
        every { this@mockk.canReceivePayments } returns canReceivePayments
        every { onboardingDashboardUrl } returns dashboardUrl
        every { canCollectDonations() } returns
            (state == MollieConnectionState.ACTIVE && onboardingStatus == MollieOnboardingStatus.COMPLETED && canReceivePayments)
    }

    @Test
    fun `id is MOLLIE`() {
        assertEquals(PaymentProviderId.MOLLIE, adapter.id)
    }

    @Nested
    inner class Readiness {

        @Test
        fun `no connection is NOT_CONNECTED`() {
            every { connectionRepository.findByAssociationId(associationId) } returns null

            assertEquals(ProviderReadiness(ProviderReadinessStatus.NOT_CONNECTED), adapter.readiness(associationId))
        }

        @Test
        fun `broken connection is BROKEN even when KYC is complete`() {
            every { connectionRepository.findByAssociationId(associationId) } returns
                connection(state = MollieConnectionState.BROKEN)

            val readiness = adapter.readiness(associationId)

            assertEquals(ProviderReadinessStatus.BROKEN, readiness.status)
            assertNull(readiness.actionUrl)
        }

        @Test
        fun `active connection that cannot collect is PENDING with the onboarding wizard link`() {
            every { connectionRepository.findByAssociationId(associationId) } returns connection(
                onboardingStatus = MollieOnboardingStatus.NEEDS_DATA,
                canReceivePayments = false,
                dashboardUrl = "https://my.mollie.com/dashboard/onboarding",
            )

            val readiness = adapter.readiness(associationId)

            assertEquals(ProviderReadinessStatus.PENDING, readiness.status)
            assertEquals("https://my.mollie.com/dashboard/onboarding", readiness.actionUrl)
            assertFalse(readiness.isReady)
        }

        @Test
        fun `completed KYC without payments capability is PENDING`() {
            every { connectionRepository.findByAssociationId(associationId) } returns
                connection(canReceivePayments = false)

            assertEquals(ProviderReadinessStatus.PENDING, adapter.readiness(associationId).status)
        }

        @Test
        fun `connection that can collect is READY`() {
            every { connectionRepository.findByAssociationId(associationId) } returns connection()

            val readiness = adapter.readiness(associationId)

            assertEquals(ProviderReadiness(ProviderReadinessStatus.READY), readiness)
            assertTrue(readiness.isReady)
        }
    }

    @Nested
    inner class IsLinkStarted {

        @Test
        fun `an existing connection means the link is started`() {
            every { connectionRepository.findByAssociationId(associationId) } returns connection()

            assertTrue(adapter.isLinkStarted(associationId))
            verify(exactly = 0) { oauthStateRepository.existsByAssociationIdAndExpiresAtAfter(any(), any()) }
        }

        @Test
        fun `a non-expired OAuth state means the link is started`() {
            every { connectionRepository.findByAssociationId(associationId) } returns null
            every { oauthStateRepository.existsByAssociationIdAndExpiresAtAfter(associationId, any()) } returns true

            assertTrue(adapter.isLinkStarted(associationId))
        }

        @Test
        fun `an expired OAuth state does not count`() {
            every { connectionRepository.findByAssociationId(associationId) } returns null
            // The repository query only matches states expiring after the threshold: the threshold must be "now",
            // so that a state that expired before the call yields false.
            val threshold = slot<Instant>()
            every { oauthStateRepository.existsByAssociationIdAndExpiresAtAfter(associationId, capture(threshold)) } returns false

            val before = Instant.now()
            assertFalse(adapter.isLinkStarted(associationId))
            val after = Instant.now()

            assertFalse(threshold.captured.isBefore(before))
            assertFalse(threshold.captured.isAfter(after))
        }

        @Test
        fun `nothing at all means the link is not started`() {
            every { connectionRepository.findByAssociationId(associationId) } returns null
            every { oauthStateRepository.existsByAssociationIdAndExpiresAtAfter(any(), any()) } returns false

            assertFalse(adapter.isLinkStarted(associationId))
        }
    }

    @Nested
    inner class CreateCheckout {

        private val campaignId = UUID.randomUUID()
        private val donorProfileId = UUID.randomUUID()
        private val publicRef = UUID.randomUUID()

        private fun request(sourceSite: String? = null, campaignName: String = "Puits au Sahel") = CheckoutRequest(
            associationId = associationId,
            campaignId = campaignId,
            campaignName = campaignName,
            donorProfileId = donorProfileId,
            amount = BigDecimal("25.00"),
            publicRef = publicRef,
            widgetToken = "wt_123",
            locale = "en",
            sourceSite = sourceSite,
        )

        private fun payment(checkoutUrl: String? = "https://www.mollie.com/checkout/tr_abc") = MolliePayment(
            id = "tr_abc",
            status = MolliePaymentStatus.OPEN,
            amount = BigDecimal("25.00"),
            checkoutUrl = checkoutUrl,
            metadata = emptyMap(),
        )

        private fun givenReadyAssociation() {
            every { connectionRepository.findByAssociationId(associationId) } returns connection()
            every { tokenManager.getValidAccessToken(associationId) } returns "access_tok"
            every { mollieClient.getFirstProfileId("access_tok") } returns "pfl_1"
        }

        private fun stubCreatePayment(
            result: MolliePayment = payment(),
            redirectUrl: io.mockk.CapturingSlot<String> = slot(),
            cancelUrl: io.mockk.CapturingSlot<String> = slot(),
            description: io.mockk.CapturingSlot<String> = slot(),
            metadata: io.mockk.CapturingSlot<Map<String, String>> = slot(),
        ) {
            every {
                mollieClient.createPayment(
                    amount = BigDecimal("25.00"),
                    currency = "EUR",
                    description = capture(description),
                    redirectUrl = capture(redirectUrl),
                    cancelUrl = capture(cancelUrl),
                    webhookUrl = "https://api.example/api/webhooks/mollie",
                    metadata = capture(metadata),
                    idempotencyKey = publicRef.toString(),
                    bearerToken = "access_tok",
                    profileId = "pfl_1",
                )
            } returns result
        }

        @Test
        fun `returns a mollie-prefixed session pointing at the checkout page`() {
            givenReadyAssociation()
            stubCreatePayment()

            val session = adapter.createCheckout(request())

            assertEquals(
                CheckoutSession(
                    providerRef = "mollie:tr_abc",
                    redirectUrl = "https://www.mollie.com/checkout/tr_abc",
                    providerPaymentId = "tr_abc",
                ),
                session,
            )
        }

        @Test
        fun `builds return URLs, description and metadata without a source site`() {
            givenReadyAssociation()
            val redirectUrl = slot<String>()
            val cancelUrl = slot<String>()
            val description = slot<String>()
            val metadata = slot<Map<String, String>>()
            stubCreatePayment(redirectUrl = redirectUrl, cancelUrl = cancelUrl, description = description, metadata = metadata)

            adapter.createCheckout(request())

            val base = "https://app.example/en/embed/donate/wt_123/return"
            assertEquals("$base?ref=$publicRef", redirectUrl.captured)
            assertEquals("$base?cancelled=true", cancelUrl.captured)
            assertEquals("Don - Puits au Sahel", description.captured)
            assertEquals(
                mapOf(
                    "campaignId" to campaignId.toString(),
                    "donorProfileId" to donorProfileId.toString(),
                    "widgetToken" to "wt_123",
                ),
                metadata.captured,
            )
        }

        @Test
        fun `URL-encodes the source site on both return URLs`() {
            givenReadyAssociation()
            val redirectUrl = slot<String>()
            val cancelUrl = slot<String>()
            stubCreatePayment(redirectUrl = redirectUrl, cancelUrl = cancelUrl)

            adapter.createCheckout(request(sourceSite = "https://asso.example/don?x=1"))

            val base = "https://app.example/en/embed/donate/wt_123/return"
            val encoded = "https%3A%2F%2Fasso.example%2Fdon%3Fx%3D1"
            assertEquals("$base?ref=$publicRef&source=$encoded", redirectUrl.captured)
            assertEquals("$base?cancelled=true&source=$encoded", cancelUrl.captured)
        }

        @Test
        fun `truncates the description to 255 characters`() {
            givenReadyAssociation()
            val description = slot<String>()
            stubCreatePayment(description = description)

            adapter.createCheckout(request(campaignName = "x".repeat(300)))

            assertEquals(("Don - " + "x".repeat(300)).take(255), description.captured)
        }

        @Test
        fun `no connection is refused with 409`() {
            every { connectionRepository.findByAssociationId(associationId) } returns null

            val ex = assertThrows<ConflictException> { adapter.createCheckout(request()) }

            assertEquals("L'association n'a pas de connexion Mollie", ex.message)
            verify(exactly = 0) { mollieClient.createPayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `connection that cannot collect is refused with 409`() {
            every { connectionRepository.findByAssociationId(associationId) } returns
                connection(onboardingStatus = MollieOnboardingStatus.IN_REVIEW, canReceivePayments = false)

            val ex = assertThrows<ConflictException> { adapter.createCheckout(request()) }

            assertEquals("L'association ne peut pas encore recevoir de paiements", ex.message)
        }

        @Test
        fun `token manager failure is refused with 409`() {
            every { connectionRepository.findByAssociationId(associationId) } returns connection()
            every { tokenManager.getValidAccessToken(associationId) } throws
                IllegalStateException("Mollie connection is BROKEN for association $associationId")

            val ex = assertThrows<ConflictException> { adapter.createCheckout(request()) }

            assertEquals("Connexion Mollie de l'association interrompue", ex.message)
        }

        @Test
        fun `missing checkout URL fails with MolliePaymentException`() {
            givenReadyAssociation()
            stubCreatePayment(result = payment(checkoutUrl = null))

            val ex = assertThrows<MolliePaymentException> { adapter.createCheckout(request()) }

            assertEquals("Mollie returned no checkout URL for payment tr_abc", ex.message)
        }

        @Test
        fun `Mollie failure propagates unchanged`() {
            givenReadyAssociation()
            every { mollieClient.createPayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
                MolliePaymentException("Mollie payment creation failed: 500")

            val ex = assertThrows<MolliePaymentException> { adapter.createCheckout(request()) }

            assertEquals("Mollie payment creation failed: 500", ex.message)
        }
    }

    @Nested
    inner class RefreshPayment {

        @Test
        fun `delegates to the webhook handler with the unprefixed Mollie id`() {
            every { webhookService.handleWebhook("tr_abc") } just runs

            adapter.refreshPayment("mollie:tr_abc")

            verify(exactly = 1) { webhookService.handleWebhook("tr_abc") }
        }

        @Test
        fun `refuses a non-Mollie reference`() {
            assertThrows<IllegalArgumentException> { adapter.refreshPayment("helloasso:42") }

            verify(exactly = 0) { webhookService.handleWebhook(any()) }
        }
    }
}

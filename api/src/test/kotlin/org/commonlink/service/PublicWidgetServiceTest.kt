package org.commonlink.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.commonlink.payment.PaymentProviderId
import org.commonlink.exception.CollectionUnavailableException
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieConnection
import io.mockk.verify
import io.mockk.every
import io.mockk.mockk
import org.commonlink.config.MollieProperties
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.ConflictException
import org.commonlink.payment.MollieProviderAdapter
import org.commonlink.payment.PaymentProviderRegistry
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for the [org.commonlink.entity.AssociationProfile.status] gate added to
 * [PublicWidgetService] by IC-44 — SUSPENDED must block the whole widget/landing/donation
 * surface, ALERT must not block anything (see [AssociationStatus] KDoc).
 */
class PublicWidgetServiceTest {

    private val associationProfileRepository: AssociationProfileRepository = mockk()
    private val donationCapService: DonationCapService = mockk(relaxed = true)

    private val mollieConnectionRepository: MollieConnectionRepository = mockk(relaxed = true)
    private val guestDonorService: GuestDonorService = mockk(relaxed = true)

    private val mollieProviderAdapter = MollieProviderAdapter(
        mollieConnectionRepository = mollieConnectionRepository,
        mollieOAuthStateRepository = mockk(relaxed = true),
        mollieConnectTokenManager = mockk(relaxed = true),
        mollieClient = mockk(relaxed = true),
        mollieProperties = mockk<MollieProperties>(relaxed = true),
        mollieWebhookService = mockk(relaxed = true),
        mollieConnectService = mockk(relaxed = true),
    )

    private val service = PublicWidgetService(
        associationProfileRepository = associationProfileRepository,
        donationRepository = mockk(relaxed = true),
        donorProfileRepository = mockk(relaxed = true),
        guestDonorService = guestDonorService,
        donationService = mockk(relaxed = true),
        taxRateService = mockk(relaxed = true),
        landingPreviewTokenService = mockk(relaxed = true),
        freezeScreeningDonationService = mockk(relaxed = true),
        donationCapService = donationCapService,
        legalAcceptanceService = mockk(relaxed = true),
        paymentProviderRegistry = PaymentProviderRegistry(listOf(mollieProviderAdapter)),
    )

    private fun campaign(status: CampaignStatus = CampaignStatus.LIVE) = Campaign(
        id = UUID.randomUUID(),
        association = mockk(relaxed = true),
        name = "Campagne test",
        goal = BigDecimal("1000.00"),
        raised = BigDecimal.ZERO,
        status = status,
    )

    private fun association(status: AssociationStatus, campaign: Campaign) = AssociationProfile(
        id = UUID.randomUUID(),
        user = User(email = "asso@example.org", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL),
        name = "Association test",
        identifier = "W123456789",
        widgetDestinationCampaign = campaign,
        status = status,
    )

    @Test
    fun `getWidget refuses when association is SUSPENDED even though the campaign is LIVE`() {
        val campaign = campaign(CampaignStatus.LIVE)
        val association = association(AssociationStatus.SUSPENDED, campaign)
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association)

        val ex = assertThrows<ConflictException> { service.getWidget("clk_x") }
        assertEquals("Campaign is not accepting donations", ex.message)
    }

    @Test
    fun `getWidget succeeds when association is ALERT — ALERT does not block`() {
        val campaign = campaign(CampaignStatus.LIVE)
        val association = association(AssociationStatus.ALERT, campaign)
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association)
        every { donationCapService.remainingCapacity(campaign) } returns BigDecimal("1000.00")

        val dto = service.getWidget("clk_x")

        assertEquals(campaign.id, dto.campaignId)
    }

    @Test
    fun `getWidget succeeds when association is ACTIVE`() {
        val campaign = campaign(CampaignStatus.LIVE)
        val association = association(AssociationStatus.ACTIVE, campaign)
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association)
        every { donationCapService.remainingCapacity(campaign) } returns BigDecimal("1000.00")

        val dto = service.getWidget("clk_x")

        assertEquals(campaign.id, dto.campaignId)
    }

    @Test
    fun `createDonation refuses when association is SUSPENDED`() {
        val campaign = campaign(CampaignStatus.LIVE)
        val association = association(AssociationStatus.SUSPENDED, campaign)
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association)

        val request = CreateGuestDonationRequest(
            amount = BigDecimal("10.00"),
            donorEmail = "donor@example.org",
            donorFullName = "Jean Donateur",
            donorAddressLine1 = "1 rue de la Paix",
            donorPostalCode = "75001",
            donorCity = "Paris",
            consent = true,
            cguAccepted = true,
            cgvAccepted = true,
        )

        assertThrows<ConflictException> { service.createDonation("clk_x", request) }
    }

    // ── Collection availability (lot D) ──────────────────────────────────────

    private fun readyConnection() = mockk<MollieConnection> {
        every { state } returns MollieConnectionState.ACTIVE
        every { canCollectDonations() } returns true
    }

    private fun request() = CreateGuestDonationRequest(
        amount = BigDecimal("10.00"),
        donorEmail = "donor@example.org",
        donorFullName = "Jean Donateur",
        donorAddressLine1 = "1 rue de la Paix",
        donorPostalCode = "75001",
        donorCity = "Paris",
        consent = true,
        cguAccepted = true,
        cgvAccepted = true,
    )

    @Test
    fun `getWidget exposes collectAvailable false when the provider is not ready`() {
        val campaign = campaign(CampaignStatus.LIVE)
        val association = association(AssociationStatus.ACTIVE, campaign)
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association)
        every { mollieConnectionRepository.findByAssociationId(association.id!!) } returns null

        assertFalse(service.getWidget("clk_x").collectAvailable)
    }

    @Test
    fun `getWidget exposes collectAvailable true when the provider is ready`() {
        val campaign = campaign(CampaignStatus.LIVE)
        val association = association(AssociationStatus.ACTIVE, campaign)
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association)
        every { mollieConnectionRepository.findByAssociationId(association.id!!) } returns readyConnection()

        assertTrue(service.getWidget("clk_x").collectAvailable)
    }

    @Test
    fun `getWidget exposes collectAvailable false for a HELLOASSO association, Mollie data ignored`() {
        val campaign = campaign(CampaignStatus.LIVE)
        val association = association(AssociationStatus.ACTIVE, campaign).apply { paymentProvider = PaymentProviderId.HELLOASSO }
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association)
        every { mollieConnectionRepository.findByAssociationId(association.id!!) } returns readyConnection()

        assertFalse(service.getWidget("clk_x").collectAvailable)
    }

    @Test
    fun `createDonation refuses an unavailable collection before creating any guest donor`() {
        val campaign = campaign(CampaignStatus.LIVE)
        val association = association(AssociationStatus.ACTIVE, campaign)
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association)
        every { mollieConnectionRepository.findByAssociationId(association.id!!) } returns null

        assertThrows<CollectionUnavailableException> { service.createDonation("clk_x", request()) }

        verify(exactly = 0) { guestDonorService.findOrCreateGuestDonor(any(), any()) }
        verify(exactly = 0) { donationCapService.requireWithinCap(any(), any()) }
    }
}

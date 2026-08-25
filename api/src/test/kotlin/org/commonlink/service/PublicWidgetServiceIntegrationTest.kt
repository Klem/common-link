package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import jakarta.persistence.EntityManager
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.dto.DonationPublicStatus
import org.commonlink.entity.BudgetSide
import org.commonlink.entity.CampaignBudgetItem
import org.commonlink.entity.CampaignBudgetSection
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.MollieConnection
import org.commonlink.exception.CollectionCapExceededException
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignBudgetItemRepository
import org.commonlink.repository.CampaignBudgetSectionRepository
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Integration tests for [PublicWidgetService.createDonation] — T4 and T5 invariants:
 * - T4 : donor identity snapshot (donorFullName, addressLine1, postalCode, city, country)
 *        is persisted on the [org.commonlink.entity.Donation] row at creation time
 * - T4 : anonymousDisplay=true sets DonorProfile.anonymous without wiping identity fields
 * - T5 : sourceSite is sanitised to scheme+host only (path/query stripped)
 * - T5 : invalid sourceSite is stored as null
 *
 * [MockkBean] replaces [MollieClient] so no real HTTP calls are made.
 */
@Tag("testcontainers")
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@Transactional
class PublicWidgetServiceIntegrationTest {

    @Autowired private lateinit var publicWidgetService: PublicWidgetService
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var sectionRepository: CampaignBudgetSectionRepository
    @Autowired private lateinit var itemRepository: CampaignBudgetItemRepository
    @Autowired private lateinit var milestoneRepository: CampaignMilestoneRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var landingPreviewTokenService: LandingPreviewTokenService

    @MockkBean
    private lateinit var mollieClient: MollieClient

    @MockkBean
    private lateinit var mollieConnectionRepository: MollieConnectionRepository

    @MockkBean
    private lateinit var mollieConnectTokenManager: MollieConnectTokenManager

    @MockkBean
    private lateinit var freezeScreeningDonationService: FreezeScreeningDonationService

    private val widgetToken = "clk_integ_test"

    @BeforeEach
    fun setup() {
        val assocUser = userRepository.save(TestFixtures.associationUser(email = "wi-assoc-${System.nanoTime()}@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))

        assoc.widgetToken = widgetToken
        assoc.widgetDestinationCampaign = campaign
        associationProfileRepository.save(assoc)

        // Fully onboarded connection: KYC completed, payments authorised, link not broken.
        val mockConnection = mockk<MollieConnection> { every { canCollectDonations() } returns true }
        every { mollieConnectionRepository.findByAssociationId(any()) } returns mockConnection
        every { mollieConnectTokenManager.getValidAccessToken(any()) } returns "test_assoc_token"

        every { mollieClient.getFirstProfileId(any()) } returns "pfl_test"
        every { mollieClient.createPayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MolliePayment(
                id = "tr_integ_${System.nanoTime()}",
                status = MolliePaymentStatus.OPEN,
                amount = BigDecimal("25.00"),
                checkoutUrl = "https://checkout.mollie.com/pay/tr_integ",
                metadata = emptyMap(),
            )

        // Default: freeze check passes — individual tests override for blocking scenarios.
        every { freezeScreeningDonationService.runFreezeCheck(any(), any(), any()) } returns ScreeningOutcome.CLEAR
    }

    // ── T4 : identity snapshot ────────────────────────────────────────────────

    @Test
    fun `createDonation persists all required identity fields on the Donation row`() {
        val req = validRequest(
            donorFullName    = "Marie Curie",
            donorAddressLine1 = "1 rue Pierre et Marie Curie",
            donorAddressLine2 = "Bâtiment B",
            donorPostalCode  = "75005",
            donorCity        = "Paris",
            donorCountry     = "FR",
        )

        val response = publicWidgetService.createDonation(widgetToken, req)

        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")
        assertNotNull(donation, "Donation row must be created")
        assertEquals("Marie Curie", donation!!.donorFullName)
        assertEquals("1 rue Pierre et Marie Curie", donation.donorAddressLine1)
        assertEquals("Bâtiment B", donation.donorAddressLine2)
        assertEquals("75005", donation.donorPostalCode)
        assertEquals("Paris", donation.donorCity)
        assertEquals("FR", donation.donorCountry)
        assertNull(donation.confirmedAt, "Donation must stay pending after initiation")
    }

    @Test
    fun `createDonation with anonymousDisplay=true keeps identity fields intact on Donation`() {
        val req = validRequest(
            donorFullName    = "Jean Dupont",
            donorAddressLine1 = "12 rue de la Paix",
            anonymousDisplay = true,
        )

        val response = publicWidgetService.createDonation(widgetToken, req)

        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")
        assertNotNull(donation)
        assertEquals("Jean Dupont", donation!!.donorFullName, "Identity must be snapshotted even with anonymousDisplay=true")
        assertEquals("12 rue de la Paix", donation.donorAddressLine1)

        val donorProfile = donorProfileRepository.findAll()
            .first { it.user.email == "anon@example.com" }
        assertTrue(donorProfile.anonymous, "DonorProfile.anonymous must be true when anonymousDisplay=true")
    }

    @Test
    fun `createDonation never persists the birth date, even when the donor supplies it`() {
        val req = validRequest(donorBirthDate = java.time.LocalDate.of(1985, 6, 15))

        val response = publicWidgetService.createDonation(widgetToken, req)

        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")
        assertNotNull(donation)
        assertNull(
            donation!!.donorBirthDate,
            "La date de naissance sert au filtrage gel puis est jetée — elle ne doit jamais être écrite",
        )
        assertNull(donation.donorBirthCity, "Le widget ne collecte aucune ville de naissance")
    }

    @Test
    fun `createDonation succeeds without any birth date`() {
        val req = validRequest(donorBirthDate = null)

        val response = publicWidgetService.createDonation(widgetToken, req)

        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")
        assertNotNull(donation, "Une date de naissance absente ne doit pas empêcher le don")
        assertNull(donation!!.donorBirthDate)
    }

    // ── T5 : sourceSite sanitisation ─────────────────────────────────────────

    @Test
    fun `createDonation preserves path and query from sourceSite - strips fragment only`() {
        val req = validRequest(sourceSite = "https://example.com/some/path?q=hello&ref=abc#section")

        val response = publicWidgetService.createDonation(widgetToken, req)

        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")
        assertNotNull(donation)
        assertEquals("https://example.com/some/path?q=hello&ref=abc", donation!!.sourceSite,
            "sourceSite must preserve path+query and strip fragment only")
    }

    @Test
    fun `createDonation stores null for invalid sourceSite`() {
        val req = validRequest(sourceSite = "not-a-valid-url")

        val response = publicWidgetService.createDonation(widgetToken, req)

        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")
        assertNotNull(donation)
        assertNull(donation!!.sourceSite, "Invalid sourceSite must be stored as null")
    }

    @Test
    fun `createDonation stores null when sourceSite is blank`() {
        val req = validRequest(sourceSite = "   ")

        val response = publicWidgetService.createDonation(widgetToken, req)

        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")
        assertNotNull(donation)
        assertNull(donation!!.sourceSite)
    }

    // ── getLanding : budget projection and lazy loading ───────────────────────

    @Test
    fun `getLanding returns expense budget projection and milestone, revenue items excluded`() {
        val assoc = associationProfileRepository.findByWidgetToken(widgetToken).get()
        val campaign = assoc.widgetDestinationCampaign!!

        val expenseSection = sectionRepository.save(
            CampaignBudgetSection(campaign = campaign, side = BudgetSide.EXPENSE, code = "CHARGES", name = "Charges")
        )
        itemRepository.saveAll(listOf(
            CampaignBudgetItem(section = expenseSection, label = "Gros poste", amount = BigDecimal("700")),
            CampaignBudgetItem(section = expenseSection, label = "Petit poste", amount = BigDecimal("300")),
        ))

        val revenueSection = sectionRepository.save(
            CampaignBudgetSection(campaign = campaign, side = BudgetSide.REVENUE, code = "PRODUITS", name = "Produits")
        )
        itemRepository.save(CampaignBudgetItem(section = revenueSection, label = "Subvention", amount = BigDecimal("500")))

        milestoneRepository.save(TestFixtures.milestone(campaign, sortOrder = 0))

        entityManager.flush()
        entityManager.clear()

        val dto = publicWidgetService.getLanding(widgetToken)

        assertEquals(2, dto.budget.size)
        assertEquals("Gros poste", dto.budget[0].label)
        assertEquals(70, dto.budget[0].percentage)
        assertEquals("Petit poste", dto.budget[1].label)
        assertEquals(30, dto.budget[1].percentage)
        assertFalse(dto.budget.any { it.label == "Subvention" }, "Revenue items must not appear in budget")
        assertEquals(1, dto.milestones.size)
        assertEquals(66, dto.taxReductionRate)
    }

    // ── Landing preview : contournement du gate LIVE ─────────────────────────

    @Test
    fun `getLanding - non-LIVE campaign without preview token is refused`() {
        setCampaignStatus(CampaignStatus.DRAFT)

        assertThrows<ConflictException> { publicWidgetService.getLanding(widgetToken) }
    }

    @Test
    fun `getLanding - non-LIVE campaign with a valid preview token is served`() {
        val assocId = associationProfileRepository.findByWidgetToken(widgetToken).get().id!!
        setCampaignStatus(CampaignStatus.DRAFT)
        val (preview, _) = landingPreviewTokenService.issue(assocId)

        val dto = publicWidgetService.getLanding(widgetToken, preview)

        assertNotNull(dto.campaignName)
        // The form must be rendered disabled: createDonation would refuse this campaign.
        assertFalse(dto.donationsEnabled)
    }

    @Test
    fun `getLanding - preview token of another association is refused`() {
        setCampaignStatus(CampaignStatus.DRAFT)
        val (foreignPreview, _) = landingPreviewTokenService.issue(UUID.randomUUID())

        assertThrows<ConflictException> { publicWidgetService.getLanding(widgetToken, foreignPreview) }
    }

    @Test
    fun `getLanding - forged preview token is refused`() {
        setCampaignStatus(CampaignStatus.DRAFT)

        assertThrows<ConflictException> { publicWidgetService.getLanding(widgetToken, "not-a-jwt") }
    }

    @Test
    fun `getLanding - unknown widget token stays a 404 even with a valid preview token`() {
        val assocId = associationProfileRepository.findByWidgetToken(widgetToken).get().id!!
        val (preview, _) = landingPreviewTokenService.issue(assocId)

        // A preview relaxes the LIVE check only — it never conjures a widget into existence.
        assertThrows<NotFoundException> { publicWidgetService.getLanding("clk_does_not_exist", preview) }
    }

    @Test
    fun `getLanding - LIVE campaign ignores an invalid preview token`() {
        val dto = publicWidgetService.getLanding(widgetToken, "garbage")

        assertNotNull(dto.campaignName)
        assertTrue(dto.donationsEnabled)
    }

    @Test
    fun `createDonation - a preview token cannot unlock donations on a non-LIVE campaign`() {
        // The donation path resolves the widget through resolveWidget, which knows nothing about previews.
        setCampaignStatus(CampaignStatus.DRAFT)

        assertThrows<ConflictException> { publicWidgetService.createDonation(widgetToken, validRequest()) }
    }

    // ── Freeze screening — LCB-FT art. L.561-5 ───────────────────────────────

    @Test
    fun `createDonation - listed donor triggers ConflictException and Mollie payment is never created`() {
        every { freezeScreeningDonationService.runFreezeCheck(any(), any(), any()) } returns ScreeningOutcome.HIT

        val countBefore = donationRepository.count()
        assertThrows<ConflictException> { publicWidgetService.createDonation(widgetToken, validRequest()) }

        verify(exactly = 0) { mollieClient.createPayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(countBefore, donationRepository.count(), "Donation count must not increase on a freeze HIT")
    }

    @Test
    fun `createDonation - small-amount listed donor is refused identically (no amount threshold)`() {
        every { freezeScreeningDonationService.runFreezeCheck(any(), any(), any()) } returns ScreeningOutcome.HIT
        val smallAmountRequest = validRequest().copy(amount = BigDecimal("1.00"))

        assertThrows<ConflictException> { publicWidgetService.createDonation(widgetToken, smallAmountRequest) }

        verify(exactly = 0) { mollieClient.createPayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `createDonation - screening unavailable blocks donation and Mollie is never called`() {
        every { freezeScreeningDonationService.runFreezeCheck(any(), any(), any()) } returns ScreeningOutcome.UNAVAILABLE

        assertThrows<ConflictException> { publicWidgetService.createDonation(widgetToken, validRequest()) }

        verify(exactly = 0) { mollieClient.createPayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `createDonation - freeze HIT and UNAVAILABLE produce identical ConflictException messages`() {
        every { freezeScreeningDonationService.runFreezeCheck(any(), any(), any()) } returns ScreeningOutcome.HIT
        val hitEx = assertThrows<ConflictException> { publicWidgetService.createDonation(widgetToken, validRequest()) }

        every { freezeScreeningDonationService.runFreezeCheck(any(), any(), any()) } returns ScreeningOutcome.UNAVAILABLE
        val unavailableEx = assertThrows<ConflictException> {
            publicWidgetService.createDonation(widgetToken, validRequest(donorEmail = "other@example.com"))
        }

        assertEquals(hitEx.message, unavailableEx.message, "Freeze HIT and UNAVAILABLE must produce identical messages")
    }

    // ── Collection cap ────────────────────────────────────────────────────────

    /**
     * The cap must bite *before* Mollie is called: after that a payable checkout URL exists and the
     * only remaining option would be a refund, which is exactly what the cap prevents.
     */
    @Test
    fun `createDonation - donation above the cap is refused and Mollie is never called`() {
        // Fixture goal is 40 000; with the default 10 % margin the cap is 44 000.
        setCampaignRaised(BigDecimal("43990.00"))

        val ex = assertThrows<CollectionCapExceededException> {
            publicWidgetService.createDonation(widgetToken, validRequest())
        }

        assertEquals(0, BigDecimal("10.00").compareTo(ex.remainingCapacity))
        verify(exactly = 0) { mollieClient.createPayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    /**
     * A cap refusal must leave nothing behind: it happens before the guest donor is provisioned, so a
     * refused attempt creates neither a donor profile nor a screening journal entry.
     */
    @Test
    fun `createDonation - a capped refusal provisions no donor and runs no screening`() {
        setCampaignRaised(BigDecimal("44000.00"))
        val donorsBefore = donorProfileRepository.count()

        assertThrows<CollectionCapExceededException> {
            publicWidgetService.createDonation(widgetToken, validRequest(donorEmail = "capped@example.com"))
        }

        assertEquals(donorsBefore, donorProfileRepository.count(), "No guest donor may be provisioned")
        verify(exactly = 0) { freezeScreeningDonationService.runFreezeCheck(any(), any(), any()) }
    }

    @Test
    fun `createDonation - donation within the margin above the goal is accepted`() {
        // 40 000 collected: the goal is met, but the 10 % margin leaves 4 000 of capacity.
        setCampaignRaised(BigDecimal("40000.00"))

        val response = publicWidgetService.createDonation(widgetToken, validRequest())

        assertNotNull(response.checkoutUrl)
    }

    /**
     * A payment session already open holds its amount against the cap, so two donors checking out at
     * the same time cannot collectively overshoot.
     */
    @Test
    fun `createDonation - an open payment session holds capacity against the next donor`() {
        setCampaignRaised(BigDecimal("43950.00"))

        // First donor takes 25 of the remaining 50 — the row stays pending (confirmedAt null).
        publicWidgetService.createDonation(widgetToken, validRequest(donorEmail = "first@example.com"))
        entityManager.flush()

        // Second donor asking for 30 exceeds the 25 still free.
        val ex = assertThrows<CollectionCapExceededException> {
            publicWidgetService.createDonation(
                widgetToken,
                validRequest(donorEmail = "second@example.com").copy(amount = BigDecimal("30.00")),
            )
        }
        assertEquals(0, BigDecimal("25.00").compareTo(ex.remainingCapacity))
    }

    @Test
    fun `getWidget exposes the remaining capacity`() {
        setCampaignRaised(BigDecimal("43000.00"))

        val widget = publicWidgetService.getWidget(widgetToken)

        assertEquals(0, BigDecimal("1000.00").compareTo(widget.remainingCapacity))
    }

    /** Sets the confirmed total of the destination campaign and makes it visible to the service. */
    private fun setCampaignRaised(raised: BigDecimal) {
        val assoc = associationProfileRepository.findByWidgetToken(widgetToken).get()
        val campaign = assoc.widgetDestinationCampaign!!
        campaign.raised = raised
        campaignRepository.save(campaign)
        entityManager.flush()
        entityManager.clear()
    }

    /** Flips the destination campaign status and makes the change visible to the service. */
    private fun setCampaignStatus(status: CampaignStatus) {
        val assoc = associationProfileRepository.findByWidgetToken(widgetToken).get()
        val campaign = assoc.widgetDestinationCampaign!!
        campaign.status = status
        campaignRepository.save(campaign)
        entityManager.flush()
        entityManager.clear()
    }

    // ── Tracking payload on the Mollie redirect URL ──────────────────────────

    @Test
    fun `createDonation embeds the dataLayer tracking payload and a fresh public ref on redirectUrl`() {
        val req = validRequest(anonymousDisplay = true)

        publicWidgetService.createDonation(widgetToken, req)

        val redirectUrlSlot = slot<String>()
        verify(exactly = 1) {
            mollieClient.createPayment(
                amount = any(),
                currency = any(),
                description = any(),
                redirectUrl = capture(redirectUrlSlot),
                cancelUrl = any(),
                webhookUrl = any(),
                metadata = any(),
                idempotencyKey = any(),
                bearerToken = any(),
                profileId = any(),
            )
        }
        val redirectUrl = redirectUrlSlot.captured
        assertTrue(redirectUrl.contains("&currency=EUR"), redirectUrl)
        assertTrue(redirectUrl.contains("&amount=25.00"), redirectUrl)
        assertTrue(redirectUrl.contains("&anonymous=true"), redirectUrl)
        assertTrue(redirectUrl.contains("&campaignId="), redirectUrl)
        assertTrue(redirectUrl.contains("&campaignName="), redirectUrl)
        assertTrue(redirectUrl.contains("&associationName="), redirectUrl)
        assertTrue(Regex("ref=[0-9a-fA-F-]{36}").containsMatchIn(redirectUrl), "redirectUrl must carry a fresh public ref: $redirectUrl")
    }

    @Test
    fun `createDonation never embeds the tracking payload on cancelUrl`() {
        publicWidgetService.createDonation(widgetToken, validRequest())

        val cancelUrlSlot = slot<String>()
        verify(exactly = 1) {
            mollieClient.createPayment(
                amount = any(),
                currency = any(),
                description = any(),
                redirectUrl = any(),
                cancelUrl = capture(cancelUrlSlot),
                webhookUrl = any(),
                metadata = any(),
                idempotencyKey = any(),
                bearerToken = any(),
                profileId = any(),
            )
        }
        val cancelUrl = cancelUrlSlot.captured
        assertTrue(cancelUrl.contains("cancelled=true"), cancelUrl)
        assertFalse(cancelUrl.contains("ref="), "cancelUrl must not carry the tracking payload: $cancelUrl")
    }

    // ── Donation status polling (public_ref) ─────────────────────────────────

    @Test
    fun `getDonationStatus returns PENDING without a method before confirmation`() {
        val response = publicWidgetService.createDonation(widgetToken, validRequest())
        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")!!

        val status = publicWidgetService.getDonationStatus(donation.publicRef!!)

        assertEquals(DonationPublicStatus.PENDING, status.status)
        assertNull(status.method)
    }

    @Test
    fun `getDonationStatus returns CONFIRMED with the payment method once confirmed`() {
        val response = publicWidgetService.createDonation(widgetToken, validRequest())
        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")!!
        donation.paymentMethod = "creditcard"
        donation.confirmedAt = java.time.Instant.now()
        donationRepository.save(donation)
        entityManager.flush()
        entityManager.clear()

        val status = publicWidgetService.getDonationStatus(donation.publicRef!!)

        assertEquals(DonationPublicStatus.CONFIRMED, status.status)
        assertEquals("creditcard", status.method)
    }

    @Test
    fun `getDonationStatus throws NotFoundException for an unknown ref`() {
        assertThrows<NotFoundException> { publicWidgetService.getDonationStatus(UUID.randomUUID()) }
    }

    @Test
    fun `getDonationStatus confirms immediately from a live Mollie check, without waiting for the webhook`() {
        val response = publicWidgetService.createDonation(widgetToken, validRequest())
        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")!!

        every { mollieClient.getPayment(response.paymentId, bearerToken = "test_assoc_token") } returns
            MolliePayment(
                id = response.paymentId,
                status = MolliePaymentStatus.PAID,
                amount = BigDecimal("25.00"),
                checkoutUrl = null,
                metadata = emptyMap(),
                method = "creditcard",
            )

        val status = publicWidgetService.getDonationStatus(donation.publicRef!!)

        assertEquals(DonationPublicStatus.CONFIRMED, status.status)
        assertEquals("creditcard", status.method)

        val persisted = donationRepository.findByProviderRef("mollie:${response.paymentId}")!!
        assertNotNull(persisted.confirmedAt, "Live confirmation must persist confirmedAt, not just the response")

        val campaign = associationProfileRepository.findByWidgetToken(widgetToken).get().widgetDestinationCampaign!!
        entityManager.clear()
        assertEquals(0, BigDecimal("25.00").compareTo(campaignRepository.findById(campaign.id!!).get().raised),
            "Live confirmation must credit the campaign exactly like the webhook path does")
    }

    @Test
    fun `getDonationStatus stays PENDING when the live Mollie check fails, instead of propagating`() {
        val response = publicWidgetService.createDonation(widgetToken, validRequest())
        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")!!

        every { mollieClient.getPayment(response.paymentId, bearerToken = "test_assoc_token") } throws
            RuntimeException("Mollie unreachable")

        val status = publicWidgetService.getDonationStatus(donation.publicRef!!)

        assertEquals(DonationPublicStatus.PENDING, status.status)
        assertNull(status.method)
    }

    @Test
    fun `getDonationStatus does not attempt a live check once already confirmed`() {
        val response = publicWidgetService.createDonation(widgetToken, validRequest())
        val donation = donationRepository.findByProviderRef("mollie:${response.paymentId}")!!
        donation.paymentMethod = "creditcard"
        donation.confirmedAt = java.time.Instant.now()
        donationRepository.save(donation)
        entityManager.flush()
        entityManager.clear()

        publicWidgetService.getDonationStatus(donation.publicRef!!)

        verify(exactly = 0) { mollieClient.getPayment(any(), any()) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun validRequest(
        donorEmail: String = "anon@example.com",
        donorFullName: String = "Jean Dupont",
        donorBirthDate: java.time.LocalDate? = java.time.LocalDate.of(1985, 6, 15),
        donorAddressLine1: String = "12 rue de la Paix",
        donorAddressLine2: String? = null,
        donorPostalCode: String = "75001",
        donorCity: String = "Paris",
        donorCountry: String = "FR",
        anonymousDisplay: Boolean = false,
        sourceSite: String? = "https://example.com",
    ) = CreateGuestDonationRequest(
        amount            = BigDecimal("25.00"),
        donorEmail        = donorEmail,
        donorFullName     = donorFullName,
        donorBirthDate    = donorBirthDate,
        donorAddressLine1 = donorAddressLine1,
        donorAddressLine2 = donorAddressLine2,
        donorPostalCode   = donorPostalCode,
        donorCity         = donorCity,
        donorCountry      = donorCountry,
        anonymousDisplay  = anonymousDisplay,
        consent           = true,
        sourceSite        = sourceSite,
    )
}

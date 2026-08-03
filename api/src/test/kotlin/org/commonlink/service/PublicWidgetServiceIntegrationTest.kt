package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import jakarta.persistence.EntityManager
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.entity.BudgetSide
import org.commonlink.entity.CampaignBudgetItem
import org.commonlink.entity.CampaignBudgetSection
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.MollieConnection
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

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

    @MockkBean
    private lateinit var mollieClient: MollieClient

    @MockkBean
    private lateinit var mollieConnectionRepository: MollieConnectionRepository

    @MockkBean
    private lateinit var mollieConnectTokenManager: MollieConnectTokenManager

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun validRequest(
        donorEmail: String = "anon@example.com",
        donorFullName: String = "Jean Dupont",
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

package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import org.commonlink.entity.CampaignStatus
import org.commonlink.event.DonationConfirmedEvent
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Integration tests for [MollieWebhookService] — T1 invariants:
 * - double paid webhook → 1 confirmed donation + 1 [DonationConfirmedEvent]
 * - non-paid statuses (canceled/failed/authorized) → donation stays unconfirmed
 * - guest donor receives a derived wallet address after confirmation
 *
 * [MockkBean] replaces [MollieClient] so no real HTTP calls are made.
 * [RecordApplicationEvents] captures Spring application events published within the test transaction.
 */
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@RecordApplicationEvents
@Transactional
class MollieWebhookServiceTest {

    @Autowired private lateinit var mollieWebhookService: MollieWebhookService
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var events: ApplicationEvents

    @MockkBean
    private lateinit var mollieClient: MollieClient

    @MockkBean
    private lateinit var mollieConnectTokenManager: MollieConnectTokenManager

    private lateinit var donorProfileId: UUID
    private lateinit var campaignId: UUID

    @BeforeEach
    fun setup() {
        val donorUser = userRepository.save(TestFixtures.donorUser(email = "wh-donor-${System.nanoTime()}@example.com"))
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        donorProfileId = donor.id!!

        val assocUser = userRepository.save(TestFixtures.associationUser(email = "wh-assoc-${System.nanoTime()}@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))
        campaignId = campaign.id!!

        every { mollieConnectTokenManager.getValidAccessToken(any()) } returns "test_assoc_token"
    }

    // ── T1 : idempotence webhook ─────────────────────────────────────────────

    @Test
    fun `double paid webhook - confirms donation exactly once and publishes exactly one DonationConfirmedEvent`() {
        val mollieId = "tr_idem_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        createPendingDonation(providerRef)

        every { mollieClient.getPayment(mollieId, any()) } returns paidPayment(mollieId)

        mollieWebhookService.handleWebhook(mollieId)
        mollieWebhookService.handleWebhook(mollieId)

        val donation = donationRepository.findByProviderRef(providerRef)
        assertNotNull(donation?.confirmedAt, "Donation must be confirmed after first webhook")

        val confirmedEvents = events.stream(DonationConfirmedEvent::class.java).toList()
        assertEquals(1, confirmedEvents.size, "Exactly one DonationConfirmedEvent must be published")
        assertEquals(donation!!.id, confirmedEvents[0].donationId)
    }

    // ── T1 : non-paid statuses → donation stays unconfirmed ──────────────────

    @Test
    fun `canceled webhook - donation stays unconfirmed, no event published`() {
        val mollieId = "tr_canceled_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        createPendingDonation(providerRef)
        every { mollieClient.getPayment(mollieId, any()) } returns fakePayment(mollieId, MolliePaymentStatus.CANCELED)

        mollieWebhookService.handleWebhook(mollieId)

        assertNull(donationRepository.findByProviderRef(providerRef)?.confirmedAt)
        assertEquals(0, events.stream(DonationConfirmedEvent::class.java).count())
    }

    @Test
    fun `failed webhook - donation stays unconfirmed`() {
        val mollieId = "tr_failed_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        createPendingDonation(providerRef)
        every { mollieClient.getPayment(mollieId, any()) } returns fakePayment(mollieId, MolliePaymentStatus.FAILED)

        mollieWebhookService.handleWebhook(mollieId)

        assertNull(donationRepository.findByProviderRef(providerRef)?.confirmedAt)
    }

    @Test
    fun `authorized webhook - donation stays unconfirmed (no-op, not a confirmation)`() {
        val mollieId = "tr_auth_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        createPendingDonation(providerRef)
        every { mollieClient.getPayment(mollieId, any()) } returns fakePayment(mollieId, MolliePaymentStatus.AUTHORIZED)

        mollieWebhookService.handleWebhook(mollieId)

        assertNull(donationRepository.findByProviderRef(providerRef)?.confirmedAt)
        assertEquals(0, events.stream(DonationConfirmedEvent::class.java).count())
    }

    // ── T1 guest : wallet dérivé sur paid webhook ─────────────────────────────

    @Test
    fun `paid webhook for guest donor - wallet address is derived after confirmation`() {
        val mollieId = "tr_guest_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"

        val guestDonor = donorProfileRepository.findById(donorProfileId).orElseThrow()
        assertNull(guestDonor.walletAddress, "No wallet address before first donation")
        createPendingDonation(providerRef)

        every { mollieClient.getPayment(mollieId, any()) } returns paidPayment(mollieId)

        mollieWebhookService.handleWebhook(mollieId)

        val updatedDonor = donorProfileRepository.findById(donorProfileId).orElseThrow()
        assertNotNull(updatedDonor.walletAddress, "Wallet address must be derived after confirmation")
        assertTrue(updatedDonor.walletAddress!!.startsWith("0x"), "EVM address starts with 0x")
        assertEquals(42, updatedDonor.walletAddress!!.length, "EVM address is 42 chars")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createPendingDonation(providerRef: String) {
        donationRepository.save(
            TestFixtures.donation(
                donor = donorProfileRepository.findById(donorProfileId).orElseThrow(),
                campaign = campaignRepository.findById(campaignId).orElseThrow(),
                providerRef = providerRef,
                confirmedAt = null,
            )
        )
    }

    private fun paidPayment(id: String) = MolliePayment(
        id = id,
        status = MolliePaymentStatus.PAID,
        amount = BigDecimal("25.00"),
        checkoutUrl = null,
        metadata = mapOf(
            "donorProfileId" to donorProfileId.toString(),
            "campaignId" to campaignId.toString(),
        ),
    )

    private fun fakePayment(id: String, status: MolliePaymentStatus) = MolliePayment(
        id = id,
        status = status,
        amount = BigDecimal("10.00"),
        checkoutUrl = null,
        metadata = emptyMap(),
    )
}

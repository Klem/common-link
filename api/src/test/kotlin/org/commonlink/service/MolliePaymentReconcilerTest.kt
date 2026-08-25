package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.CampaignStatus
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Integration tests for [MolliePaymentReconciler] — the backstop for donations left pending
 * because a Mollie webhook was never delivered or failed processing.
 *
 * [MollieClient] and [MollieConnectTokenManager] are mocked (same collaborators
 * [MollieWebhookServiceTest] mocks — the reconciler drives [MollieWebhookService] itself, so no
 * real HTTP call is made). [TechnicalAlertService] is mocked too so tests assert on the alert
 * call directly instead of depending on e-mail delivery.
 */
@Tag("testcontainers")
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
    "app.mollie.reconciler.stale-after-minutes=15",
])
@Transactional
class MolliePaymentReconcilerTest {

    @Autowired private lateinit var reconciler: MolliePaymentReconciler
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository

    @MockkBean
    private lateinit var mollieClient: MollieClient

    @MockkBean
    private lateinit var mollieConnectTokenManager: MollieConnectTokenManager

    @MockkBean
    private lateinit var technicalAlertService: TechnicalAlertService

    private lateinit var donorProfileId: UUID
    private lateinit var campaignId: UUID

    @BeforeEach
    fun setup() {
        val donorUser = userRepository.save(TestFixtures.donorUser(email = "reconciler-donor-${System.nanoTime()}@example.com"))
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        donorProfileId = donor.id!!

        val assocUser = userRepository.save(TestFixtures.associationUser(email = "reconciler-assoc-${System.nanoTime()}@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))
        campaignId = campaign.id!!

        every { mollieConnectTokenManager.getValidAccessToken(any()) } returns "test_assoc_token"
        every { technicalAlertService.reportFailure(any(), any(), any(), any()) } returns Unit
    }

    private fun stalePendingDonation(providerRef: String) =
        donationRepository.save(
            TestFixtures.donation(
                donor = donorProfileRepository.findById(donorProfileId).orElseThrow(),
                campaign = campaignRepository.findById(campaignId).orElseThrow(),
                providerRef = providerRef,
                confirmedAt = null,
                createdAt = Instant.now().minus(30, ChronoUnit.MINUTES),
            )
        )

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

    private fun pendingPayment(id: String) = MolliePayment(
        id = id,
        status = MolliePaymentStatus.PENDING,
        amount = BigDecimal("25.00"),
        checkoutUrl = null,
        metadata = emptyMap(),
    )

    @Test
    fun `stale donation Mollie already confirmed - reconciler confirms it and alerts MISSED_WEBHOOK`() {
        val mollieId = "tr_missed_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        stalePendingDonation(providerRef)
        every { mollieClient.getPayment(mollieId, any()) } returns paidPayment(mollieId)

        reconciler.reconcile()

        verify(exactly = 1) { mollieClient.getPayment(mollieId, any()) }

        val donation = donationRepository.findByProviderRef(providerRef)
        assertNotNull(donation?.confirmedAt, "Reconciler must self-heal the donation")

        val kindSlot = slot<TechnicalAlertKind>()
        val pathSlot = slot<String>()
        verify(exactly = 1) {
            technicalAlertService.reportFailure(capture(kindSlot), null, capture(pathSlot), null)
        }
        assertEquals(TechnicalAlertKind.MISSED_WEBHOOK, kindSlot.captured)
        assertEquals(providerRef, pathSlot.captured)
    }

    @Test
    fun `stale donation still pending at Mollie - no confirmation, no alert`() {
        val mollieId = "tr_stillpending_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        stalePendingDonation(providerRef)
        every { mollieClient.getPayment(mollieId, any()) } returns pendingPayment(mollieId)

        reconciler.reconcile()

        verify(exactly = 1) { mollieClient.getPayment(mollieId, any()) }
        assertNull(donationRepository.findByProviderRef(providerRef)?.confirmedAt)
        verify(exactly = 0) { technicalAlertService.reportFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `donation not yet stale - reconciler ignores it`() {
        val mollieId = "tr_fresh_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        donationRepository.save(
            TestFixtures.donation(
                donor = donorProfileRepository.findById(donorProfileId).orElseThrow(),
                campaign = campaignRepository.findById(campaignId).orElseThrow(),
                providerRef = providerRef,
                confirmedAt = null,
                createdAt = Instant.now(),
            )
        )

        reconciler.reconcile()

        verify(exactly = 0) { mollieClient.getPayment(any(), any()) }
        verify(exactly = 0) { technicalAlertService.reportFailure(any(), any(), any(), any()) }
    }

    /**
     * Not a race test: `findStalePending` filters on `confirmedAt IS NULL`, so an already-confirmed
     * donation is excluded from the candidate set outright — this asserts that exclusion, not the
     * concurrent-confirmation race itself. The actual race (reconciler picks up a stale row, the
     * real webhook confirms it between the query and [MollieWebhookService.handleWebhook] running)
     * is covered by [MollieWebhookServiceTest]'s idempotence test on [DonationService.recordPayment]
     * — [MolliePaymentReconciler] calls that exact code path, so it inherits the same guarantee.
     */
    @Test
    fun `already-confirmed donation is excluded from the stale-pending candidate set`() {
        val mollieId = "tr_already_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        val donation = donationRepository.save(
            TestFixtures.donation(
                donor = donorProfileRepository.findById(donorProfileId).orElseThrow(),
                campaign = campaignRepository.findById(campaignId).orElseThrow(),
                providerRef = providerRef,
                confirmedAt = Instant.now(),
                createdAt = Instant.now().minus(30, ChronoUnit.MINUTES),
            )
        )
        assertNotNull(donation.confirmedAt)

        reconciler.reconcile()

        verify(exactly = 0) { mollieClient.getPayment(any(), any()) }
        verify(exactly = 0) { technicalAlertService.reportFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `stale donation with a non-Mollie providerRef is skipped without touching Mollie`() {
        val providerRef = "stripe:pi_${UUID.randomUUID()}"
        donationRepository.save(
            TestFixtures.donation(
                donor = donorProfileRepository.findById(donorProfileId).orElseThrow(),
                campaign = campaignRepository.findById(campaignId).orElseThrow(),
                providerRef = providerRef,
                confirmedAt = null,
                createdAt = Instant.now().minus(30, ChronoUnit.MINUTES),
            )
        )

        reconciler.reconcile()

        verify(exactly = 0) { mollieClient.getPayment(any(), any()) }
        verify(exactly = 0) { technicalAlertService.reportFailure(any(), any(), any(), any()) }
        assertNull(donationRepository.findByProviderRef(providerRef)?.confirmedAt)
    }
}

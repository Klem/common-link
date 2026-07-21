package org.commonlink.service

import org.commonlink.entity.OnchainJobAction
import org.commonlink.event.DonationConfirmedEvent
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.OnchainJobRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.util.AopTestUtils
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
    "onchain.worker.enabled=true",
])
@Transactional
class DonationReceiptServiceTest {

    @Autowired private lateinit var donationReceiptService: DonationReceiptService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var onchainJobRepository: OnchainJobRepository
    // PDF generation uses Postgres-specific SQL — mock to keep tests H2-compatible
    @MockkBean private lateinit var receiptService: ReceiptService
    @MockkBean private lateinit var receiptNumberService: ReceiptNumberService

    @BeforeEach
    fun setUpMocks() {
        every { receiptNumberService.nextNumber(any(), any()) } returns "2026-0001"
        every { receiptService.generate(any(), any()) } returns ByteArray(32)
    }

    @Test
    fun `enqueueOnchainJob creates exactly one RECORD_DONATION job`() {
        val (donationId) = setupConfirmedDonation()

        donationReceiptService.enqueueOnchainJob(donationId)

        val jobs = onchainJobRepository.findAll()
            .filter { it.action == OnchainJobAction.RECORD_DONATION }
            .filter { it.correlationKey == "DONATION:$donationId" }
        assertEquals(1, jobs.size, "Expected exactly one RECORD_DONATION job")
        assertNotNull(jobs[0].payloadJson)
    }

    @Test
    fun `enqueueOnchainJob is idempotent — second call does not create duplicate`() {
        val (donationId) = setupConfirmedDonation()

        donationReceiptService.enqueueOnchainJob(donationId)
        donationReceiptService.enqueueOnchainJob(donationId)

        val jobs = onchainJobRepository.findAll()
            .filter { it.action == OnchainJobAction.RECORD_DONATION }
            .filter { it.correlationKey == "DONATION:$donationId" }
        assertEquals(1, jobs.size, "Second call must not create a second job")
    }

    @Test
    fun `findConfirmedWithoutOnchainJob returns donation before job is enqueued`() {
        val (donationId) = setupConfirmedDonation()

        val pending = donationRepository.findConfirmedWithoutOnchainJob()
        val found = pending.any { it.id == donationId }
        assertEquals(true, found, "Confirmed donation without job should appear in reconciler query")
    }

    @Test
    fun `findConfirmedWithoutOnchainJob excludes donation once job is enqueued`() {
        val (donationId) = setupConfirmedDonation()

        donationReceiptService.enqueueOnchainJob(donationId)

        val pending = donationRepository.findConfirmedWithoutOnchainJob()
        val found = pending.any { it.id == donationId }
        assertEquals(false, found, "Donation with job should not appear in reconciler query")
    }

    // ── T2 : event listener wiring ───────────────────────────────────────────

    @Test
    fun `onDonationConfirmed event handler calls enqueueOnchainJob and creates RECORD_DONATION job`() {
        val (donationId) = setupConfirmedDonation()

        // Bypass @Async proxy so the method runs synchronously on the test thread.
        // self.enqueueOnchainJob still goes through the Spring proxy → @Transactional applies.
        val rawService = AopTestUtils.getUltimateTargetObject<DonationReceiptService>(donationReceiptService)
        rawService.onDonationConfirmed(DonationConfirmedEvent(donationId))

        val jobs = onchainJobRepository.findAll()
            .filter { it.action == OnchainJobAction.RECORD_DONATION }
            .filter { it.correlationKey == "DONATION:$donationId" }
        assertEquals(1, jobs.size, "onDonationConfirmed must enqueue exactly one RECORD_DONATION job")
        assertNotNull(jobs[0].payloadJson)
    }

    // ── Setup helper ─────────────────────────────────────────────────────────

    private data class TestDonation(val donationId: java.util.UUID)

    private fun setupConfirmedDonation(): TestDonation {
        val donorUser = userRepository.save(TestFixtures.donorUser(email = "receipt-test-${System.nanoTime()}@example.com"))
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))

        // Wallet address must be set — it would normally be derived by DonationService.confirmDonation
        donor.walletAddress = "0x" + "a".repeat(40)
        donorProfileRepository.save(donor)

        val assocUser = userRepository.save(TestFixtures.associationUser(email = "receipt-assoc-${System.nanoTime()}@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc))

        val donation = donationRepository.save(
            TestFixtures.donation(
                donor = donor,
                campaign = campaign,
                confirmedAt = Instant.now(),
            )
        )
        return TestDonation(donation.id!!)
    }
}

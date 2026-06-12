package org.commonlink.service

import org.commonlink.entity.OnchainJobAction
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.OnchainJobRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@Transactional
class DonationServiceIdempotencyTest {

    @Autowired private lateinit var donationService: DonationService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var onchainJobRepository: OnchainJobRepository

    private lateinit var donorProfileId: UUID
    private lateinit var campaignId: UUID

    @BeforeEach
    fun setup() {
        val donorUser = userRepository.save(TestFixtures.donorUser(email = "donor-idem@example.com"))
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        donorProfileId = donor.id!!

        val assocUser = userRepository.save(TestFixtures.associationUser(email = "assoc-idem@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc))
        campaignId = campaign.id!!
    }

    @Test
    fun `same providerRef called twice enqueues RECORD_DONATION exactly once`() {
        val providerRef = "monerium:${UUID.randomUUID()}"
        val receipt = ByteArray(32) { it.toByte() }

        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("50.00"), receipt)
        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("50.00"), receipt)

        val jobs = onchainJobRepository.findAll()
            .filter { it.action == OnchainJobAction.RECORD_DONATION }
            .filter { it.correlationKey?.startsWith("DONATION:") == true }

        assertEquals(1, jobs.size, "Expected exactly one RECORD_DONATION job, got ${jobs.size}")
    }

    @Test
    fun `already-confirmed donation is skipped on second call`() {
        val providerRef = "stripe:pi_${UUID.randomUUID()}"
        val receipt = ByteArray(32) { it.toByte() }

        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("25.00"), receipt)

        val donation = donationRepository.findByProviderRef(providerRef)!!
        val confirmedAt = donation.confirmedAt
        assert(confirmedAt != null) { "Donation should be confirmed after first call" }

        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("25.00"), receipt)

        val reloaded = donationRepository.findByProviderRef(providerRef)!!
        assertEquals(confirmedAt, reloaded.confirmedAt, "confirmedAt must not change on second call")
    }
}

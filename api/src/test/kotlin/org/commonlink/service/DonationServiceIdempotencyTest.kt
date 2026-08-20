package org.commonlink.service

import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.util.UUID

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
class DonationServiceIdempotencyTest {

    @Autowired private lateinit var donationService: DonationService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var entityManager: jakarta.persistence.EntityManager

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
    fun `same providerRef called twice confirms donation exactly once`() {
        val providerRef = "mollie:tr_${UUID.randomUUID()}"

        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("50.00"))
        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("50.00"))

        val donations = donationRepository.findAll()
            .filter { it.providerRef == providerRef }
        assertEquals(1, donations.size, "Expected exactly one Donation row")
        assertNotNull(donations[0].confirmedAt, "Donation must be confirmed")
    }

    @Test
    fun `already-confirmed donation is skipped on second call`() {
        val providerRef = "mollie:tr_${UUID.randomUUID()}"

        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("25.00"))

        val donation = donationRepository.findByProviderRef(providerRef)!!
        val confirmedAt = donation.confirmedAt
        assertNotNull(confirmedAt) { "Donation should be confirmed after first call" }

        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("25.00"))

        val reloaded = donationRepository.findByProviderRef(providerRef)!!
        assertEquals(confirmedAt, reloaded.confirmedAt, "confirmedAt must not change on second call")
    }

    /**
     * `campaigns.raised` used to be written by nobody: every reader — campaign DTOs, the widget
     * progress bar, the next-milestone query, and now the collection cap — saw the value frozen at
     * insert time. Confirmation is its single writer.
     */
    @Test
    fun `confirming a donation credits the campaign raised amount`() {
        val before = campaignRepository.findById(campaignId).get().raised

        donationService.recordPayment("mollie:tr_${UUID.randomUUID()}", donorProfileId, campaignId, BigDecimal("40.00"))
        donationService.recordPayment("mollie:tr_${UUID.randomUUID()}", donorProfileId, campaignId, BigDecimal("2.50"))
        entityManager.flush()
        entityManager.clear()

        val after = campaignRepository.findById(campaignId).get().raised
        assertEquals(0, before.add(BigDecimal("42.50")).compareTo(after), "raised must be $before + 42.50, was $after")
    }

    /** A replayed webhook must not credit the campaign twice. */
    @Test
    fun `replaying the same payment credits the campaign only once`() {
        val providerRef = "mollie:tr_${UUID.randomUUID()}"
        val before = campaignRepository.findById(campaignId).get().raised

        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("30.00"))
        donationService.recordPayment(providerRef, donorProfileId, campaignId, BigDecimal("30.00"))
        entityManager.flush()
        entityManager.clear()

        val after = campaignRepository.findById(campaignId).get().raised
        assertEquals(0, before.add(BigDecimal("30.00")).compareTo(after), "raised must be credited once, was $after")
    }
}

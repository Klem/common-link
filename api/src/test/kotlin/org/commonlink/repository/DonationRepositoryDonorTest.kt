package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.CampaignStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.util.UUID

@ImportTestcontainers(TestcontainersConfig::class)
class DonationRepositoryDonorTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val donorProfileRepository: DonorProfileRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val donationRepository: DonationRepository,
) : AbstractRepositoryTest() {

    private lateinit var campaignId: UUID
    private lateinit var donorId1: UUID
    private lateinit var donorId2: UUID
    private lateinit var anonymousDonorId: UUID

    @BeforeEach
    fun setup() {
        val assocUser = userRepository.save(TestFixtures.associationUser())
        val assoc     = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign  = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))
        campaignId = campaign.id!!

        val donorUser1 = userRepository.save(TestFixtures.donorUser(email = "d1@test.com"))
        val donor1 = donorProfileRepository.save(TestFixtures.donorProfile(donorUser1, displayName = "Marie Dupont", anonymous = false))
        donorId1 = donor1.id!!

        val donorUser2 = userRepository.save(TestFixtures.donorUser(email = "d2@test.com"))
        val donor2 = donorProfileRepository.save(TestFixtures.donorProfile(donorUser2, displayName = "Pierre Martin", anonymous = false))
        donorId2 = donor2.id!!

        val anonUser = userRepository.save(TestFixtures.donorUser(email = "anon@test.com"))
        val anonDonor = donorProfileRepository.save(TestFixtures.donorProfile(anonUser, displayName = "Marie Secret", anonymous = true))
        anonymousDonorId = anonDonor.id!!

        // donor1: 2 confirmed donations
        donationRepository.save(TestFixtures.donation(donor1, campaign, amount = BigDecimal("100.00")))
        donationRepository.save(TestFixtures.donation(donor1, campaign, amount = BigDecimal("50.00")))
        // donor2: 1 confirmed donation
        donationRepository.save(TestFixtures.donation(donor2, campaign, amount = BigDecimal("200.00")))
        // anon donor: 1 confirmed donation
        donationRepository.save(TestFixtures.donation(anonDonor, campaign, amount = BigDecimal("75.00")))
        // donor1: 1 unconfirmed — must NOT appear in aggregates
        donationRepository.save(TestFixtures.donation(donor1, campaign, amount = BigDecimal("999.00"), confirmedAt = null))
    }

    @Test
    fun `findDonorAggregatesByCampaignId returns one row per donor with correct sums`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "totalAmount"))
        val page = donationRepository.findDonorAggregatesByCampaignId(campaignId, pageable)

        // 3 confirmed donors (donor1, donor2, anonDonor)
        assertThat(page.totalElements).isEqualTo(3)
        val rows = page.content.associateBy { it.getDonorId() }

        val row1 = rows[donorId1]!!
        assertThat(row1.getTotalAmount()).isEqualByComparingTo("150.00")
        assertThat(row1.getTxCount()).isEqualTo(2L)
        assertThat(row1.getLastDonationAt()).isNotNull()
        assertThat(row1.getAnonymous()).isFalse()

        val row2 = rows[donorId2]!!
        assertThat(row2.getTotalAmount()).isEqualByComparingTo("200.00")
        assertThat(row2.getTxCount()).isEqualTo(1L)

        val rowAnon = rows[anonymousDonorId]!!
        assertThat(rowAnon.getAnonymous()).isTrue()
        assertThat(rowAnon.getTotalAmount()).isEqualByComparingTo("75.00")
    }

    @Test
    fun `findDonorAggregatesByCampaignId excludes unconfirmed donations from sums`() {
        val pageable = PageRequest.of(0, 20)
        val page = donationRepository.findDonorAggregatesByCampaignId(campaignId, pageable)

        val row1 = page.content.first { it.getDonorId() == donorId1 }
        // 100 + 50 = 150 (the 999 unconfirmed must not be counted)
        assertThat(row1.getTotalAmount()).isEqualByComparingTo("150.00")
    }

    @Test
    fun `findDonorAggregatesByCampaignIdAndSearch returns matching non-anonymous donors only`() {
        val pageable = PageRequest.of(0, 20)
        // "Marie" matches donor1 (displayName "Marie Dupont") and anonymousDonor (displayName "Marie Secret")
        // but anonymous donors must be excluded from search results
        val page = donationRepository.findDonorAggregatesByCampaignIdAndSearch(campaignId, "Marie", pageable)

        assertThat(page.content).hasSize(1)
        assertThat(page.content[0].getDonorId()).isEqualTo(donorId1)
        assertThat(page.content[0].getAnonymous()).isFalse()
    }

    @Test
    fun `findDonorAggregatesByCampaignIdAndSearch is case insensitive`() {
        val pageable = PageRequest.of(0, 20)
        val page = donationRepository.findDonorAggregatesByCampaignIdAndSearch(campaignId, "pierre", pageable)

        assertThat(page.content).hasSize(1)
        assertThat(page.content[0].getDonorId()).isEqualTo(donorId2)
    }

    @Test
    fun `findDonorAggregatesByCampaignIdAndSearch returns empty when no match`() {
        val pageable = PageRequest.of(0, 20)
        val page = donationRepository.findDonorAggregatesByCampaignIdAndSearch(campaignId, "zzznomatch", pageable)

        assertThat(page.content).isEmpty()
    }

    @Test
    fun `findByDonorIdAndCampaignId returns donations for specific donor`() {
        val pageable = PageRequest.of(0, 20)
        val page = donationRepository.findByDonorIdAndCampaignId(donorId1, campaignId, pageable)

        // donor1 has 2 confirmed + 1 unconfirmed = 3 total (no confirmed_at filter here)
        assertThat(page.totalElements).isEqualTo(3)
    }

    @Test
    fun `findByDonorIdAndCampaignId returns empty for unknown donor`() {
        val pageable = PageRequest.of(0, 20)
        val page = donationRepository.findByDonorIdAndCampaignId(UUID.randomUUID(), campaignId, pageable)

        assertThat(page.content).isEmpty()
    }

    @Test
    fun `findDonorAggregatesByCampaignId supports pagination`() {
        val page0 = donationRepository.findDonorAggregatesByCampaignId(campaignId, PageRequest.of(0, 2))
        val page1 = donationRepository.findDonorAggregatesByCampaignId(campaignId, PageRequest.of(1, 2))

        assertThat(page0.content).hasSize(2)
        assertThat(page1.content).hasSize(1)
        assertThat(page0.totalElements).isEqualTo(3)
    }
}

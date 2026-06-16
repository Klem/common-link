package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.CampaignStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@ImportTestcontainers(TestcontainersConfig::class)
class DonationRepositoryDashboardTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val donorProfileRepository: DonorProfileRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val donationRepository: DonationRepository,
) : AbstractRepositoryTest() {

    private lateinit var assocId: java.util.UUID

    @BeforeEach
    fun setup() {
        val assocUser = userRepository.save(TestFixtures.associationUser())
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        assocId = assoc.id!!

        val campaign = campaignRepository.save(
            TestFixtures.campaign(assoc, status = CampaignStatus.LIVE)
        )

        val donorUser1 = userRepository.save(TestFixtures.donorUser(email = "d1@test.com"))
        val donor1 = donorProfileRepository.save(TestFixtures.donorProfile(donorUser1, displayName = "Donor One"))

        val donorUser2 = userRepository.save(TestFixtures.donorUser(email = "d2@test.com"))
        val donor2 = donorProfileRepository.save(TestFixtures.donorProfile(donorUser2, displayName = "Donor Two"))

        // Current month donations
        donationRepository.save(TestFixtures.donation(donor1, campaign, amount = BigDecimal("100.00")))
        donationRepository.save(TestFixtures.donation(donor1, campaign, amount = BigDecimal("200.00")))
        // Different donor
        donationRepository.save(TestFixtures.donation(donor2, campaign, amount = BigDecimal("50.00")))
        // Unconfirmed — must NOT count in aggregates
        donationRepository.save(TestFixtures.donation(donor1, campaign, amount = BigDecimal("999.00"), confirmedAt = null))
    }

    @Test
    fun `sumConfirmedAmountByAssociationId sums only confirmed donations on LIVE campaigns`() {
        val total = donationRepository.sumConfirmedAmountByAssociationId(assocId)
        assertThat(total).isEqualByComparingTo("350.00") // 100 + 200 + 50
    }

    @Test
    fun `sumConfirmedAmountByAssociationId excludes other association`() {
        val otherUser = userRepository.save(TestFixtures.associationUser(email = "other@test.com"))
        val otherAssoc = associationProfileRepository.save(TestFixtures.associationProfile(otherUser, name = "Other Assoc"))
        val total = donationRepository.sumConfirmedAmountByAssociationId(otherAssoc.id!!)
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `countDistinctDonorsByAssociationId counts unique confirmed donors`() {
        val count = donationRepository.countDistinctDonorsByAssociationId(assocId)
        assertThat(count).isEqualTo(2L) // donor1 and donor2 (unconfirmed donation of donor1 not counted)
    }

    @Test
    fun `findMonthlyAmountsByAssociationId groups by month and excludes unconfirmed`() {
        val since = YearMonth.now().minusMonths(5).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val rows = donationRepository.findMonthlyAmountsByAssociationId(assocId, since)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].getYear().toInt()).isEqualTo(YearMonth.now().year)
        assertThat(rows[0].getMonth().toInt()).isEqualTo(YearMonth.now().monthValue)
        assertThat(rows[0].getAmount()).isEqualByComparingTo("350.00")
    }

    @Test
    fun `findMonthlyAmountsByAssociationId excludes donations before since`() {
        // 'since' set to one hour in the future — nothing should match
        val rows = donationRepository.findMonthlyAmountsByAssociationId(assocId, Instant.now().plusSeconds(3600))
        assertThat(rows).isEmpty()
    }

    @Test
    fun `findRecentByAssociationId returns confirmed donations newest first`() {
        val results = donationRepository.findRecentByAssociationId(assocId, PageRequest.of(0, 10))
        assertThat(results).hasSize(3) // unconfirmed excluded
        // All should have confirmedAt set
        results.forEach { assertThat(it.confirmedAt).isNotNull() }
    }

    @Test
    fun `findRecentByAssociationId respects page size`() {
        val results = donationRepository.findRecentByAssociationId(assocId, PageRequest.of(0, 2))
        assertThat(results).hasSize(2)
    }

    @Test
    fun `findRecentByAssociationId excludes other association donations`() {
        val otherUser = userRepository.save(TestFixtures.associationUser(email = "other2@test.com"))
        val otherAssoc = associationProfileRepository.save(TestFixtures.associationProfile(otherUser, name = "Other2"))
        val results = donationRepository.findRecentByAssociationId(otherAssoc.id!!, PageRequest.of(0, 10))
        assertThat(results).isEmpty()
    }

    @Test
    fun `sumConfirmedAmountByAssociationId excludes DRAFT campaign donations`() {
        val assocUser = userRepository.save(TestFixtures.associationUser(email = "draft@test.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser, name = "Draft Assoc"))
        val draftCampaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.DRAFT))
        val donorUser = userRepository.save(TestFixtures.donorUser(email = "draftdonor@test.com"))
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        donationRepository.save(TestFixtures.donation(donor, draftCampaign, amount = BigDecimal("500.00")))

        val total = donationRepository.sumConfirmedAmountByAssociationId(assoc.id!!)
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO)
    }
}

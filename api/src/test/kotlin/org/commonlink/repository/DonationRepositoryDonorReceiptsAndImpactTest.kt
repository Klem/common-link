package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.DonationReceipt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Integration coverage for Sprint 3's two new donor queries, run against a real PostgreSQL:
 * `findReceiptedDetailRowsByDonorId`'s `EXISTS` receipt gate and `findDistinctCampaignsByDonorId`'s
 * `DISTINCT` are exactly the kind of thing a mockk-based service test cannot prove.
 */
@ImportTestcontainers(TestcontainersConfig::class)
class DonationRepositoryDonorReceiptsAndImpactTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val donorProfileRepository: DonorProfileRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val donationRepository: DonationRepository,
    @Autowired private val donationReceiptRepository: DonationReceiptRepository,
) : AbstractRepositoryTest() {

    private lateinit var donorId: UUID
    private lateinit var campaignAId: UUID
    private lateinit var campaignBId: UUID

    private val confirmedAt = Instant.parse("2025-06-01T10:00:00Z")

    @BeforeEach
    fun setup() {
        val assocUser = userRepository.save(TestFixtures.associationUser())
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))

        val campaignA = campaignRepository.save(
            TestFixtures.campaign(assoc, name = "A", status = CampaignStatus.LIVE)
        )
        campaignAId = campaignA.id!!
        val campaignB = campaignRepository.save(
            TestFixtures.campaign(assoc, name = "B", status = CampaignStatus.LIVE)
        )
        campaignBId = campaignB.id!!

        val donorUser = userRepository.save(TestFixtures.donorUser())
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        donorId = donor.id!!

        // Campaign A: one confirmed donation WITH a receipt.
        val receipted = donationRepository.save(
            TestFixtures.donation(donor, campaignA, amount = BigDecimal("100.00"), confirmedAt = confirmedAt)
        )
        donationReceiptRepository.save(
            DonationReceipt(donation = receipted, receiptNumber = "REC-0001", pdfBytes = byteArrayOf(1), generatedAt = confirmedAt)
        )
        // Campaign A: a second confirmed donation with NO receipt yet.
        donationRepository.save(
            TestFixtures.donation(donor, campaignA, amount = BigDecimal("40.00"), confirmedAt = confirmedAt)
        )
        // Campaign B: three confirmed donations, no receipt -- must collapse to one impact card.
        repeat(3) {
            donationRepository.save(
                TestFixtures.donation(donor, campaignB, amount = BigDecimal("10.00"), confirmedAt = confirmedAt)
            )
        }
    }

    @Test
    fun `findReceiptedDetailRowsByDonorId excludes a confirmed donation with no receipt`() {
        val rows = donationRepository.findReceiptedDetailRowsByDonorId(donorId)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].getAmount()).isEqualByComparingTo("100.00")
        assertThat(rows[0].getReceiptNumber()).isEqualTo("REC-0001")
    }

    @Test
    fun `findDistinctCampaignsByDonorId returns one row per campaign, not per donation`() {
        val rows = donationRepository.findDistinctCampaignsByDonorId(donorId)

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.getCampaignId() }).containsExactlyInAnyOrder(campaignAId, campaignBId)
    }
}

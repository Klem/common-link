package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.MilestoneStatus
import org.commonlink.entity.PayoutStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Integration coverage for sprint 4's three new donor-engagement-feed queries, run against a real
 * PostgreSQL. A `since IS NULL OR ... > since` guard passed a nullable bind parameter and broke in
 * production with "could not determine data type of parameter" -- exactly the kind of defect a
 * mockk-based service test cannot catch (see `DonorEngagementServiceTest`, which mocks these
 * repositories entirely). These tests exercise the real JPQL, including the `Instant.EPOCH`
 * "everything available" convention the fix settled on.
 */
@ImportTestcontainers(TestcontainersConfig::class)
class DonorEngagementRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val donorProfileRepository: DonorProfileRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val campaignMilestoneRepository: CampaignMilestoneRepository,
    @Autowired private val donationRepository: DonationRepository,
    @Autowired private val payeeRepository: PayeeRepository,
    @Autowired private val payeeIbanRepository: PayeeIbanRepository,
    @Autowired private val payoutRepository: PayoutRepository,
) : AbstractRepositoryTest() {

    private lateinit var donorId: UUID
    private lateinit var supportedCampaignId: UUID

    private val t1 = Instant.parse("2026-01-01T00:00:00Z")
    private val t2 = Instant.parse("2026-02-01T00:00:00Z")

    @BeforeEach
    fun setup() {
        val donorUser = userRepository.save(TestFixtures.donorUser())
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        donorId = donor.id!!

        val assocUser = userRepository.save(TestFixtures.associationUser())
        val supportedAssoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val supportedCampaign = campaignRepository.save(
            TestFixtures.campaign(supportedAssoc, name = "Supported", status = CampaignStatus.LIVE),
        )
        supportedCampaignId = supportedCampaign.id!!
        donationRepository.save(
            TestFixtures.donation(donor, supportedCampaign, amount = BigDecimal("50.00"), confirmedAt = t1),
        )

        // A second campaign of the SAME association, not itself funded by the donor -- payouts on
        // it must still surface (association-wide transparency), matching resolveDonationUrl's use
        // of the association, not the campaign.
        val otherCampaignSameAssoc = campaignRepository.save(
            TestFixtures.campaign(supportedAssoc, name = "Same assoc, other campaign", status = CampaignStatus.LIVE),
        )

        // A campaign of a DIFFERENT, unsupported association -- must never surface.
        val otherAssocUser = userRepository.save(TestFixtures.associationUser(email = "other@example.com"))
        val otherAssoc = associationProfileRepository.save(TestFixtures.associationProfile(otherAssocUser))
        val unsupportedCampaign = campaignRepository.save(
            TestFixtures.campaign(otherAssoc, name = "Unsupported", status = CampaignStatus.LIVE),
        )

        val payee = payeeRepository.save(TestFixtures.payee(supportedAssoc))
        val iban = payeeIbanRepository.save(TestFixtures.payeeIban(payee))

        payoutRepository.save(
            TestFixtures.payout(supportedCampaign, payee, iban, status = PayoutStatus.CONFIRMED)
                .also { it.confirmedAt = t1 },
        )
        payoutRepository.save(
            TestFixtures.payout(otherCampaignSameAssoc, payee, iban, status = PayoutStatus.CONFIRMED)
                .also { it.confirmedAt = t2 },
        )
        val otherPayee = payeeRepository.save(TestFixtures.payee(otherAssoc))
        val otherIban = payeeIbanRepository.save(TestFixtures.payeeIban(otherPayee))
        payoutRepository.save(
            TestFixtures.payout(unsupportedCampaign, otherPayee, otherIban, status = PayoutStatus.CONFIRMED)
                .also { it.confirmedAt = t2 },
        )

        campaignMilestoneRepository.save(
            TestFixtures.milestone(supportedCampaign, title = "Reached", status = MilestoneStatus.REACHED)
                .also { it.reachedAt = t1 },
        )
        campaignMilestoneRepository.save(
            TestFixtures.milestone(supportedCampaign, title = "Not reached", status = MilestoneStatus.CURRENT),
        )
        campaignMilestoneRepository.save(
            TestFixtures.milestone(unsupportedCampaign, title = "Reached elsewhere", status = MilestoneStatus.REACHED)
                .also { it.reachedAt = t1 },
        )
    }

    @Test
    fun `findConfirmedSinceForDonor with EPOCH returns every confirmed payout of supported associations`() {
        val rows = payoutRepository.findConfirmedSinceForDonor(donorId, Instant.EPOCH)

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.confirmedAt }).containsExactlyInAnyOrder(t1, t2)
    }

    @Test
    fun `findConfirmedSinceForDonor excludes a payout on an unsupported association`() {
        val rows = payoutRepository.findConfirmedSinceForDonor(donorId, Instant.EPOCH)

        assertThat(rows.map { it.campaign.name }).doesNotContain("Unsupported")
    }

    @Test
    fun `findConfirmedSinceForDonor filters strictly after the given threshold`() {
        val rows = payoutRepository.findConfirmedSinceForDonor(donorId, t1)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].confirmedAt).isEqualTo(t2)
    }

    @Test
    fun `findReachedSinceForDonor returns only reached milestones of the donor's own funded campaign`() {
        val rows = campaignMilestoneRepository.findReachedSinceForDonor(donorId, Instant.EPOCH)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].title).isEqualTo("Reached")
        assertThat(rows[0].campaign.id).isEqualTo(supportedCampaignId)
    }

    @Test
    fun `findReachedSinceForDonor filters strictly after the given threshold`() {
        val rows = campaignMilestoneRepository.findReachedSinceForDonor(donorId, t1)
        assertThat(rows).isEmpty()
    }

    @Test
    fun `findCompletedSinceForDonor returns a supported campaign completed after the threshold`() {
        val campaign = campaignRepository.findById(supportedCampaignId).orElseThrow()
        campaign.status = CampaignStatus.COMPLETED
        campaignRepository.save(campaign)

        val rows = campaignRepository.findCompletedSinceForDonor(donorId, Instant.EPOCH)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].id).isEqualTo(supportedCampaignId)
    }

    @Test
    fun `findCompletedSinceForDonor excludes a campaign that is not COMPLETED`() {
        val rows = campaignRepository.findCompletedSinceForDonor(donorId, Instant.EPOCH)
        assertThat(rows).isEmpty()
    }
}

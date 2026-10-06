package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationProfile
import org.commonlink.dto.DonorCampaignStatus
import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignCause
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonationRepository.AssociationCategoryRow
import org.commonlink.repository.DonationRepository.DonorAssociationRow
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorAssociationServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository     = mockk<DonationRepository>()
    private val payoutRepository       = mockk<PayoutRepository>()
    private val associationProfileRepository = mockk<AssociationProfileRepository>()
    private val publicCampaignDirectoryService = mockk<PublicCampaignDirectoryService>()

    private val service = DonorAssociationService(
        DonorReadScope(donorProfileRepository, donationRepository),
        donationRepository,
        payoutRepository,
        associationProfileRepository,
        publicCampaignDirectoryService,
    )

    private val userId   = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val donorId  = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val assocAId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val assocBId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private val lastDonation = Instant.parse("2026-02-01T10:00:00Z")

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, emailVerified = true)
    private val donor     = DonorProfile(user = donorUser, displayName = "Marie D.").setId(donorId)

    private fun aggregate(
        assocId: UUID, name: String, total: String, campaigns: Long, at: Instant? = lastDonation,
    ) = object : DonorAssociationRow {
        override fun getAssociationId() = assocId
        override fun getName() = name
        override fun getTotalAmount(): BigDecimal = BigDecimal(total)
        override fun getCampaignCount() = campaigns
        override fun getLastDonationAt() = at
    }

    private fun categoryRow(assocId: UUID, category: CampaignCause?) = object : AssociationCategoryRow {
        override fun getAssociationId() = assocId
        override fun getCategory() = category
    }

    @Test
    fun `listAssociations returns one entry per funded association with its payout record`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns listOf(
            aggregate(assocAId, "Alpha", "150.00", 2),
            aggregate(assocBId, "Beta", "200.00", 1),
        )
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns listOf(
            categoryRow(assocAId, CampaignCause.ENFANCE_EDUCATION),
            categoryRow(assocBId, CampaignCause.ENVIRONNEMENT),
        )
        every { payoutRepository.countConfirmedByAssociationId(assocAId) } returns 4
        every { payoutRepository.countConfirmedByAssociationId(assocBId) } returns 0
        every { associationProfileRepository.findById(any()) } returns Optional.empty()

        val result = service.listAssociations(userId)

        assertThat(result).hasSize(2)
        with(result[0]) {
            assertThat(associationId).isEqualTo(assocAId)
            assertThat(name).isEqualTo("Alpha")
            assertThat(category).isEqualTo(CampaignCause.ENFANCE_EDUCATION)
            assertThat(totalDonated).isEqualByComparingTo("150.00")
            assertThat(campaignCount).isEqualTo(2)
            assertThat(publishedPayoutCount).isEqualTo(4)
            assertThat(lastDonationAt).isEqualTo(lastDonation)
        }
        assertThat(result[1].publishedPayoutCount).isZero()
    }

    @Test
    fun `listAssociations keeps the category of the most recently funded campaign`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns listOf(
            aggregate(assocAId, "Alpha", "150.00", 2),
        )
        // Rows arrive oldest first — the later one must win
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns listOf(
            categoryRow(assocAId, CampaignCause.ENFANCE_EDUCATION),
            categoryRow(assocAId, CampaignCause.SANTE),
        )
        every { payoutRepository.countConfirmedByAssociationId(assocAId) } returns 0
        every { associationProfileRepository.findById(any()) } returns Optional.empty()

        assertThat(service.listAssociations(userId)[0].category).isEqualTo(CampaignCause.SANTE)
    }

    @Test
    fun `listAssociations tolerates a campaign with no category`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns listOf(
            aggregate(assocAId, "Alpha", "10.00", 1),
        )
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns listOf(
            categoryRow(assocAId, null),
        )
        every { payoutRepository.countConfirmedByAssociationId(assocAId) } returns 0
        every { associationProfileRepository.findById(any()) } returns Optional.empty()

        assertThat(service.listAssociations(userId)[0].category).isNull()
    }

    @Test
    fun `listAssociations exposes a donation URL only when the association's widget is eligible`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns listOf(
            aggregate(assocAId, "Alpha", "10.00", 1),
            aggregate(assocBId, "Beta", "10.00", 1),
        )
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns emptyList()
        every { payoutRepository.countConfirmedByAssociationId(any()) } returns 0

        val eligibleAssociation = testAssociation(assocAId, widgetToken = "clk_a", destinationLive = true)
        val ineligibleAssociation = testAssociation(assocBId, widgetToken = null, destinationLive = false)
        every { associationProfileRepository.findById(assocAId) } returns Optional.of(eligibleAssociation)
        every { associationProfileRepository.findById(assocBId) } returns Optional.of(ineligibleAssociation)
        every { publicCampaignDirectoryService.buildDonationUrl("clk_a") } returns "https://commonlink.org/fr/lp/clk_a"

        val result = service.listAssociations(userId)

        assertThat(result.first { it.associationId == assocAId }.donationUrl)
            .isEqualTo("https://commonlink.org/fr/lp/clk_a")
        assertThat(result.first { it.associationId == assocBId }.donationUrl).isNull()
    }

    @Test
    fun `listAssociations returns an empty list for a donor who has never given`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns emptyList()

        assertThat(service.listAssociations(userId)).isEmpty()
        // No aggregates: neither the category query nor the payout counts are worth issuing
        verify(exactly = 0) { donationRepository.findAssociationCategoriesByDonorId(any()) }
        verify(exactly = 0) { payoutRepository.countConfirmedByAssociationId(any()) }
    }

    @Test
    fun `listAssociations derives campaignStatus from the widget-destination campaign`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns listOf(
            aggregate(assocAId, "Alpha", "10.00", 1),
            aggregate(assocBId, "Beta", "10.00", 1),
        )
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns emptyList()
        every { payoutRepository.countConfirmedByAssociationId(any()) } returns 0

        val liveAssociation = testAssociation(assocAId, widgetToken = "clk_a", destinationLive = true)
        val noDestinationAssociation = testAssociation(assocBId, widgetToken = null, destinationLive = false)
        every { associationProfileRepository.findById(assocAId) } returns Optional.of(liveAssociation)
        every { associationProfileRepository.findById(assocBId) } returns Optional.of(noDestinationAssociation)
        every { publicCampaignDirectoryService.buildDonationUrl("clk_a") } returns "https://commonlink.org/fr/lp/clk_a"

        val completedCampaign = Campaign(
            association = liveAssociation, name = "Campagne close", status = CampaignStatus.COMPLETED,
        )
        val completedAssociation = testAssociation(assocAId, widgetToken = "clk_c", destinationLive = false)
            .also { it.widgetDestinationCampaign = completedCampaign }

        val result = service.listAssociations(userId)
        assertThat(result.first { it.associationId == assocAId }.campaignStatus)
            .isEqualTo(DonorCampaignStatus.LIVE)
        assertThat(result.first { it.associationId == assocAId }.campaignName).isEqualTo("Campagne")
        assertThat(result.first { it.associationId == assocBId }.campaignStatus)
            .isEqualTo(DonorCampaignStatus.NONE)
        assertThat(result.first { it.associationId == assocBId }.campaignName).isNull()

        every { associationProfileRepository.findById(assocAId) } returns Optional.of(completedAssociation)
        val completedResult = service.listAssociations(userId).first { it.associationId == assocAId }
        assertThat(completedResult.campaignStatus).isEqualTo(DonorCampaignStatus.COMPLETED)
        assertThat(completedResult.campaignName).isEqualTo("Campagne close")
    }

    /** Minimal [AssociationProfile], with or without an eligible widget destination campaign. */
    private fun testAssociation(id: UUID, widgetToken: String?, destinationLive: Boolean): AssociationProfile {
        val user = User(email = "a-$id@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL, emailVerified = true)
        val association = AssociationProfile(user = user, name = "Assoc $id", identifier = "775671356").setId(id)
        association.widgetToken = widgetToken
        if (widgetToken != null) {
            val destinationCampaign = Campaign(
                association = association,
                name = "Campagne",
                status = if (destinationLive) CampaignStatus.LIVE else CampaignStatus.DRAFT,
            )
            association.widgetDestinationCampaign = destinationCampaign
        }
        return association
    }
}

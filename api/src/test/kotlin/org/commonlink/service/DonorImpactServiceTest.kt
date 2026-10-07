package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.CampaignStoryDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignCause
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorImpactServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository = mockk<DonationRepository>()
    private val campaignStoryService = mockk<CampaignStoryService>()
    private val associationProfileRepository = mockk<AssociationProfileRepository>()
    private val publicCampaignDirectoryService = mockk<PublicCampaignDirectoryService>()

    private val donorReadScope = DonorReadScope(donorProfileRepository, donationRepository)
    private val service = DonorImpactService(
        donorReadScope, donationRepository, campaignStoryService,
        associationProfileRepository, publicCampaignDirectoryService,
    )

    private val userId = UUID.randomUUID()
    private val donorId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
    private val donorProfile = DonorProfile(user = donorUser).setId(donorId)

    private fun stubDonor() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donorProfile)
        every { associationProfileRepository.findById(any()) } returns Optional.empty()
    }

    private fun campaignRow(
        id: UUID = campaignId,
        category: CampaignCause? = CampaignCause.ENFANCE_EDUCATION,
        impactGoals: String? = "50 enfants scolarisés",
        assocId: UUID = associationId,
    ): DonationRepository.DonorCampaignRow = mockk<DonationRepository.DonorCampaignRow>().also {
        every { it.getCampaignId() } returns id
        every { it.getCampaignName() } returns "Camp"
        every { it.getCampaignEmoji() } returns "🌍"
        every { it.getCategory() } returns category
        every { it.getImpactGoals() } returns impactGoals
        every { it.getAssociationId() } returns assocId
        every { it.getAssociationName() } returns "Asso"
    }

    @Test
    fun `one card per row returned by the repository`() {
        // The DISTINCT-per-campaign guarantee (one donor, three donations, one row) lives in the
        // SQL and is proven by DonationRepositoryDonorImpactTest, not here: a mock can only show
        // this service doesn't introduce its own duplication on top of what the query returns.
        stubDonor()
        every { donationRepository.findDistinctCampaignsByDonorId(donorId) } returns listOf(campaignRow())
        every { campaignStoryService.getPublishedStory(campaignId) } returns null

        val impacts = service.listImpacts(userId)

        assertThat(impacts).hasSize(1)
        assertThat(impacts[0].campaignId).isEqualTo(campaignId)
    }

    @Test
    fun `category and impactGoals are carried through`() {
        stubDonor()
        every { donationRepository.findDistinctCampaignsByDonorId(donorId) } returns
            listOf(campaignRow(category = CampaignCause.SANTE, impactGoals = "10 kits de soins"))
        every { campaignStoryService.getPublishedStory(campaignId) } returns null

        val impact = service.listImpacts(userId).single()

        assertThat(impact.category).isEqualTo(CampaignCause.SANTE)
        assertThat(impact.impactGoals).isEqualTo("10 kits de soins")
    }

    @Test
    fun `storySummary is null when the campaign has no published story`() {
        stubDonor()
        every { donationRepository.findDistinctCampaignsByDonorId(donorId) } returns listOf(campaignRow())
        every { campaignStoryService.getPublishedStory(campaignId) } returns null

        assertThat(service.listImpacts(userId).single().storySummary).isNull()
    }

    @Test
    fun `storySummary is populated when the campaign has a published story`() {
        stubDonor()
        every { donationRepository.findDistinctCampaignsByDonorId(donorId) } returns listOf(campaignRow())
        every { campaignStoryService.getPublishedStory(campaignId) } returns
            CampaignStoryDto(storyText = "<p>Un récit.</p>", storySummary = "Un résumé.", publishedAt = java.time.Instant.now())

        assertThat(service.listImpacts(userId).single().storySummary).isEqualTo("Un résumé.")
    }

    @Test
    fun `no card for a campaign without a confirmed donation`() {
        stubDonor()
        every { donationRepository.findDistinctCampaignsByDonorId(donorId) } returns emptyList()

        assertThat(service.listImpacts(userId)).isEmpty()
    }

    @Test
    fun `donationUrl is populated only when the campaign's association has an eligible widget`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donorProfile)
        every { donationRepository.findDistinctCampaignsByDonorId(donorId) } returns listOf(campaignRow())
        every { campaignStoryService.getPublishedStory(campaignId) } returns null

        val user = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL, emailVerified = true)
        val association = AssociationProfile(user = user, name = "Asso", identifier = "775671356").setId(associationId)
        association.widgetToken = "clk_x"
        association.widgetDestinationCampaign = Campaign(association = association, name = "Live one", status = CampaignStatus.LIVE)
        every { associationProfileRepository.findById(associationId) } returns Optional.of(association)
        every { publicCampaignDirectoryService.buildDonationUrl("clk_x") } returns "https://commonlink.org/fr/lp/clk_x"

        assertThat(service.listImpacts(userId).single().donationUrl).isEqualTo("https://commonlink.org/fr/lp/clk_x")
    }
}

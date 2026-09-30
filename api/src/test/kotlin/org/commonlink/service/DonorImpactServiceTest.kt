package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.CampaignStoryDto
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
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

    private val donorReadScope = DonorReadScope(donorProfileRepository, donationRepository)
    private val service = DonorImpactService(donorReadScope, donationRepository, campaignStoryService)

    private val userId = UUID.randomUUID()
    private val donorId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
    private val donorProfile = DonorProfile(user = donorUser).setId(donorId)

    private fun stubDonor() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donorProfile)
    }

    private fun campaignRow(
        id: UUID = campaignId,
        category: String? = "Éducation",
        impactGoals: String? = "50 enfants scolarisés",
    ): DonationRepository.DonorCampaignRow = mockk<DonationRepository.DonorCampaignRow>().also {
        every { it.getCampaignId() } returns id
        every { it.getCampaignName() } returns "Camp"
        every { it.getCampaignEmoji() } returns "🌍"
        every { it.getCategory() } returns category
        every { it.getImpactGoals() } returns impactGoals
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
            listOf(campaignRow(category = "Santé", impactGoals = "10 kits de soins"))
        every { campaignStoryService.getPublishedStory(campaignId) } returns null

        val impact = service.listImpacts(userId).single()

        assertThat(impact.category).isEqualTo("Santé")
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
}

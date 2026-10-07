package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.PublicCampaignRow
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.CampaignCause
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonationRepository.AssociationCategoryRow
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorRecommendationServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository = mockk<DonationRepository>()
    private val campaignRepository = mockk<CampaignRepository>()
    private val publicCampaignDirectoryService = mockk<PublicCampaignDirectoryService>()

    private val service = DonorRecommendationService(
        DonorReadScope(donorProfileRepository, donationRepository),
        donationRepository,
        campaignRepository,
        publicCampaignDirectoryService,
    )

    private val userId = UUID.randomUUID()
    private val donorId = UUID.randomUUID()
    private val supportedAssocId = UUID.randomUUID()
    private val matchingAssocId = UUID.randomUUID()
    private val otherAssocId = UUID.randomUUID()

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, emailVerified = true)
    private val donorProfile = DonorProfile(user = donorUser).setId(donorId)

    private fun stubDonor(history: List<AssociationCategoryRow> = emptyList()) {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donorProfile)
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns history
        every { publicCampaignDirectoryService.buildDonationUrl(any()) } answers {
            "https://commonlink.org/fr/lp/${firstArg<String>()}"
        }
    }

    private fun categoryRow(assocId: UUID, category: CampaignCause?) = object : AssociationCategoryRow {
        override fun getAssociationId() = assocId
        override fun getCategory() = category
    }

    private fun row(assocId: UUID, category: CampaignCause?, name: String, widgetToken: String) = PublicCampaignRow(
        campaignId = UUID.randomUUID(),
        campaignName = name,
        campaignEmoji = "🌍",
        campaignCategory = category,
        actionPlaceType = null,
        actionPlaceCode = null,
        actionPlaceLabel = null,
        actionLatitude = null,
        actionLongitude = null,
        coverImage = null,
        campaignUpdatedAt = Instant.now(),
        goal = BigDecimal("1000"),
        raised = BigDecimal("100"),
        milestoneCount = 0,
        associationId = assocId,
        associationName = name,
        associationLogo = null,
        widgetToken = widgetToken,
    )

    @Test
    fun `a campaign whose category matches the donor's history is ranked first`() {
        stubDonor(listOf(categoryRow(supportedAssocId, CampaignCause.ENFANCE_EDUCATION)))
        every { campaignRepository.findPublicLive(any()) } returns listOf(
            row(otherAssocId, CampaignCause.ENVIRONNEMENT, "Other cause", "clk_other"),
            row(matchingAssocId, CampaignCause.ENFANCE_EDUCATION, "Matching cause", "clk_match"),
        )

        val result = service.getRecommendations(userId)

        assertThat(result).hasSize(2)
        assertThat(result[0].campaignName).isEqualTo("Matching cause")
        assertThat(result[0].matchedCategory).isEqualTo(CampaignCause.ENFANCE_EDUCATION)
        assertThat(result[1].matchedCategory).isNull()
    }

    @Test
    fun `an association the donor already supports is never recommended`() {
        stubDonor(listOf(categoryRow(supportedAssocId, CampaignCause.ENFANCE_EDUCATION)))
        every { campaignRepository.findPublicLive(any()) } returns listOf(
            row(supportedAssocId, CampaignCause.ENFANCE_EDUCATION, "Already supported", "clk_supported"),
            row(otherAssocId, CampaignCause.ENVIRONNEMENT, "New cause", "clk_new"),
        )

        val result = service.getRecommendations(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].campaignName).isEqualTo("New cause")
    }

    @Test
    fun `falls back to the most recent live campaigns when the donor has no category history`() {
        stubDonor(emptyList())
        every { campaignRepository.findPublicLive(any()) } returns listOf(
            row(otherAssocId, CampaignCause.ENVIRONNEMENT, "Recent one", "clk_recent"),
        )

        val result = service.getRecommendations(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].matchedCategory).isNull()
        assertThat(result[0].donationUrl).isEqualTo("https://commonlink.org/fr/lp/clk_recent")
    }

    @Test
    fun `never returns more than 6 recommendations`() {
        stubDonor(emptyList())
        val rows = (1..10).map { i -> row(UUID.randomUUID(), null, "Campaign $i", "clk_$i") }
        every { campaignRepository.findPublicLive(any()) } returns rows

        assertThat(service.getRecommendations(userId)).hasSize(6)
    }
}

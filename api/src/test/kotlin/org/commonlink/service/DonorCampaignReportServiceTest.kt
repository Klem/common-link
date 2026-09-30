package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.dto.BudgetVarianceDto
import org.commonlink.dto.CampaignStoryDto
import org.commonlink.dto.TotalsVarianceDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.Payee
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorCampaignReportServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository = mockk<DonationRepository>()
    private val campaignRepository = mockk<CampaignRepository>()
    private val campaignMilestoneRepository = mockk<CampaignMilestoneRepository>()
    private val payoutRepository = mockk<PayoutRepository>()
    private val reportingService = mockk<ReportingService>()
    private val campaignStoryService = mockk<CampaignStoryService>()

    private val donorReadScope = DonorReadScope(donorProfileRepository, donationRepository)
    private val service = DonorCampaignReportService(
        donorReadScope, campaignRepository, campaignMilestoneRepository, payoutRepository, donationRepository,
        reportingService, campaignStoryService,
    )

    // Distinct on purpose: donorId (DonorProfile PK) must never be conflated with userId (the JWT
    // subject) — that conflation is exactly the bug that shipped in this service (403 on a donor
    // who had actually donated, see DonorReadScope).
    private val userId = UUID.randomUUID()
    private val donorId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
    private val donorProfile = DonorProfile(user = donorUser).setId(donorId)

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val association = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789").setId(UUID.randomUUID())
    private val campaign = Campaign(
        association = association, name = "Camp", emoji = "🌍", goal = BigDecimal("10000"), raised = BigDecimal("4000"), status = CampaignStatus.LIVE,
    ).setId(campaignId)
    private val payee = Payee(association = association, name = "Fournisseur").setId(UUID.randomUUID())

    private val emptyVariance = BudgetVarianceDto(
        charges = emptyList(), produits = emptyList(),
        totals = TotalsVarianceDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
    )

    private fun stubDonated(donated: Boolean = true) {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donorProfile)
        every { donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignId) } returns donated
    }

    @Test
    fun `getReport assembles hero, contribution, milestones, payouts and variance`() {
        stubDonated()
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaignId) } returns emptyList()
        val payout = Payout(
            campaign = campaign, payee = payee, payeeIbanId = UUID.randomUUID(), payeeIbanValue = "FR7630006000011234567890189",
            amount = BigDecimal("500"), kind = PayoutKind.EXPENSE, typeCode = "60-mat", label = "Achat matériel",
            status = PayoutStatus.CONFIRMED, confirmedAt = Instant.parse("2026-02-01T00:00:00Z"),
        )
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns listOf(payout)
        every { donationRepository.sumConfirmedAmountByDonorIdAndCampaignId(donorId, campaignId) } returns BigDecimal("150")
        every { reportingService.getVarianceForDonor(campaignId, donorId) } returns emptyVariance
        every { campaignStoryService.getPublishedStory(campaignId) } returns null

        val report = service.getReport(userId, campaignId)

        assertThat(report.campaignId).isEqualTo(campaignId)
        assertThat(report.campaignName).isEqualTo("Camp")
        assertThat(report.associationName).isEqualTo("Asso")
        assertThat(report.donorContribution).isEqualByComparingTo("150")
        assertThat(report.confirmedPayouts).hasSize(1)
        assertThat(report.confirmedPayouts[0].payeeName).isEqualTo("Fournisseur")
        assertThat(report.confirmedPayouts[0].sectionCode).isEqualTo("60")
        assertThat(report.variance).isSameAs(emptyVariance)
        assertThat(report.registryBannerText).isEqualTo(REGISTRY_BANNER_TEXT)
    }

    @Test
    fun `getReport reports no story when none was ever written or it is still a draft`() {
        stubDonated()
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaignId) } returns emptyList()
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns emptyList()
        every { donationRepository.sumConfirmedAmountByDonorIdAndCampaignId(donorId, campaignId) } returns BigDecimal.ZERO
        every { reportingService.getVarianceForDonor(campaignId, donorId) } returns emptyVariance
        every { campaignStoryService.getPublishedStory(campaignId) } returns null

        val report = service.getReport(userId, campaignId)

        assertThat(report.story).isNull()
    }

    @Test
    fun `getReport carries the published story through`() {
        stubDonated()
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaignId) } returns emptyList()
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns emptyList()
        every { donationRepository.sumConfirmedAmountByDonorIdAndCampaignId(donorId, campaignId) } returns BigDecimal.ZERO
        every { reportingService.getVarianceForDonor(campaignId, donorId) } returns emptyVariance
        val storyDto = CampaignStoryDto(storyText = "Un récit publié.", publishedAt = Instant.now())
        every { campaignStoryService.getPublishedStory(campaignId) } returns storyDto

        val report = service.getReport(userId, campaignId)

        assertThat(report.story).isEqualTo(storyDto)
    }

    @Test
    fun `donorContribution counts only the current donor, not other donors of the same campaign`() {
        stubDonated()
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaignId) } returns emptyList()
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns emptyList()
        // The repository query itself is donor-scoped; asserting the exact call proves the service
        // never falls back to a campaign-wide sum.
        every { donationRepository.sumConfirmedAmountByDonorIdAndCampaignId(donorId, campaignId) } returns BigDecimal("75")
        every { reportingService.getVarianceForDonor(campaignId, donorId) } returns emptyVariance
        every { campaignStoryService.getPublishedStory(campaignId) } returns null

        val report = service.getReport(userId, campaignId)

        assertThat(report.donorContribution).isEqualByComparingTo("75")
    }

    @Test
    fun `getReport refuses a donor who has not donated to the campaign`() {
        stubDonated(false)

        assertThatThrownBy { service.getReport(userId, campaignId) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `getReport throws NotFoundException for a non-existent campaign`() {
        stubDonated()
        every { campaignRepository.findById(campaignId) } returns Optional.empty()

        assertThatThrownBy { service.getReport(userId, campaignId) }
            .isInstanceOf(NotFoundException::class.java)
    }
}

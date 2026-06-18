package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.BudgetSide
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignBudgetItem
import org.commonlink.entity.CampaignBudgetSection
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignBudgetSectionRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class ReportingServiceTest {

    private val campaignRepository            = mockk<CampaignRepository>()
    private val associationProfileRepository  = mockk<AssociationProfileRepository>()
    private val sectionRepository             = mockk<CampaignBudgetSectionRepository>()
    private val payoutRepository              = mockk<PayoutRepository>()
    private val donationRepository            = mockk<DonationRepository>()

    private val service = ReportingService(
        campaignRepository, associationProfileRepository,
        sectionRepository, payoutRepository, donationRepository,
    )

    private val userId     = UUID.randomUUID()
    private val assocId    = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val assoc     = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789")
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, assocId) }
    private val campaign  = Campaign(association = assoc, name = "Camp", emoji = "🌍", description = "desc", goal = BigDecimal("10000"), status = CampaignStatus.LIVE)
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, campaignId) }

    @Test
    fun `getVariance - returns section variances for charges and produits`() {
        val chargeSection  = sectionWithItems(BudgetSide.EXPENSE, "60", "Achats", BigDecimal("1000"))
        val produitSection = sectionWithItems(BudgetSide.REVENUE, "74", "Subventions", BigDecimal("5000"))

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { sectionRepository.findAllWithItemsByCampaignId(campaignId) } returns listOf(chargeSection, produitSection)
        every { payoutRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId) } returns
            listOf(arrayOf("60-mat", BigDecimal("600")))
        every { donationRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId) } returns
            listOf(arrayOf("74", BigDecimal("3000")))

        val result = service.getVariance(campaignId, userId)

        assertThat(result.charges).hasSize(1)
        with(result.charges[0]) {
            assertThat(sectionCode).isEqualTo("60")
            assertThat(planned).isEqualByComparingTo("1000")
            assertThat(actual).isEqualByComparingTo("600")
            assertThat(variance).isEqualByComparingTo("-400")
        }
        assertThat(result.produits).hasSize(1)
        with(result.produits[0]) {
            assertThat(sectionCode).isEqualTo("74")
            assertThat(planned).isEqualByComparingTo("5000")
            assertThat(actual).isEqualByComparingTo("3000")
            assertThat(variance).isEqualByComparingTo("-2000")
        }
        with(result.totals) {
            assertThat(totalPlannedCharges).isEqualByComparingTo("1000")
            assertThat(totalActualCharges).isEqualByComparingTo("600")
            assertThat(totalPlannedProduits).isEqualByComparingTo("5000")
            assertThat(totalActualProduits).isEqualByComparingTo("3000")
        }
    }

    @Test
    fun `getVariance - empty budget returns zero totals`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { sectionRepository.findAllWithItemsByCampaignId(campaignId) } returns emptyList()
        every { payoutRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId) } returns emptyList()
        every { donationRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId) } returns emptyList()

        val result = service.getVariance(campaignId, userId)

        assertThat(result.charges).isEmpty()
        assertThat(result.produits).isEmpty()
        assertThat(result.totals.totalPlannedCharges).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totals.totalActualProduits).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `getVariance - payout without matching section has zero actual for that section`() {
        val chargeSection = sectionWithItems(BudgetSide.EXPENSE, "60", "Achats", BigDecimal("500"))

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { sectionRepository.findAllWithItemsByCampaignId(campaignId) } returns listOf(chargeSection)
        // payout has typeCode "64-rem" — no matching section "64"
        every { payoutRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId) } returns
            listOf(arrayOf("64-rem", BigDecimal("200")))
        every { donationRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId) } returns emptyList()

        val result = service.getVariance(campaignId, userId)

        assertThat(result.charges[0].actual).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `getVariance - no confirmed donations yields zero actual produits`() {
        val produitSection = sectionWithItems(BudgetSide.REVENUE, "74", "Subventions", BigDecimal("2000"))

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { sectionRepository.findAllWithItemsByCampaignId(campaignId) } returns listOf(produitSection)
        every { payoutRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId) } returns emptyList()
        every { donationRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId) } returns emptyList()

        val result = service.getVariance(campaignId, userId)

        assertThat(result.produits[0].actual).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `getVariance - throws UserNotFoundException when user has no association profile`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.empty()

        assertThatThrownBy { service.getVariance(campaignId, userId) }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun `getVariance - throws NotFoundException when campaign not found`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.empty()

        assertThatThrownBy { service.getVariance(campaignId, userId) }
            .isInstanceOf(NotFoundException::class.java)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun sectionWithItems(
        side: BudgetSide,
        code: String,
        name: String,
        itemAmount: BigDecimal,
    ): CampaignBudgetSection {
        val section = CampaignBudgetSection(campaign = campaign, side = side, code = code, name = name, sortOrder = 0)
        section.items.add(CampaignBudgetItem(section = section, label = name, amount = itemAmount, sortOrder = 0))
        return section
    }
}

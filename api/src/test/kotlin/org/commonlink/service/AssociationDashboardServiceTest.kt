package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.ActivityType
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.MilestoneStatus
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.MonthlyAmountRow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

class AssociationDashboardServiceTest {

    private val donationRepository: DonationRepository = mockk()
    private val campaignRepository: CampaignRepository = mockk()
    private val milestoneRepository: CampaignMilestoneRepository = mockk()

    private lateinit var service: AssociationDashboardService

    private val assocId: UUID = UUID.randomUUID()
    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    @BeforeEach
    fun setup() {
        service = AssociationDashboardService(donationRepository, campaignRepository, milestoneRepository)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun monthlyRow(yearMonth: String, amount: BigDecimal): MonthlyAmountRow {
        val (y, m) = yearMonth.split("-").map { it.toInt() }
        return object : MonthlyAmountRow {
            override fun getYear(): Number = y
            override fun getMonth(): Number = m
            override fun getAmount() = amount
        }
    }

    private fun campaign(raised: BigDecimal = BigDecimal.ZERO, goal: BigDecimal = BigDecimal("1000")) =
        mockk<org.commonlink.entity.Campaign> {
            every { this@mockk.raised } returns raised
            every { this@mockk.goal } returns goal
            every { this@mockk.status } returns CampaignStatus.LIVE
        }

    private fun milestone(title: String, target: BigDecimal, campaignRaised: BigDecimal): org.commonlink.entity.CampaignMilestone =
        mockk {
            every { this@mockk.title } returns title
            every { this@mockk.targetAmount } returns target
            every { this@mockk.campaign } returns campaign(raised = campaignRaised)
            every { status } returns MilestoneStatus.CURRENT
        }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `getDashboard returns full stats when data exists`() {
        val now = YearMonth.now()
        val currentMonth = now.format(monthFmt)
        val prevMonth = now.minusMonths(1).format(monthFmt)

        every { donationRepository.sumConfirmedAmountByAssociationId(assocId) } returns BigDecimal("1500.00")
        every { campaignRepository.findAllByAssociationIdAndStatus(assocId, CampaignStatus.LIVE) } returns
            listOf(campaign(raised = BigDecimal("300"), goal = BigDecimal("1000")))
        every { milestoneRepository.findNextMilestoneByAssociationId(assocId, PageRequest.of(0, 1)) } returns
            listOf(milestone("Premier palier", BigDecimal("500"), BigDecimal("300")))
        every { donationRepository.findMonthlyAmountsByAssociationId(assocId, any()) } returns
            listOf(monthlyRow(prevMonth, BigDecimal("500")), monthlyRow(currentMonth, BigDecimal("1000")))
        every { donationRepository.findRecentByAssociationId(assocId, PageRequest.of(0, 10)) } returns emptyList()

        val result = service.getDashboard(assocId)

        assertThat(result.totalRaisedActive).isEqualByComparingTo("1500.00")
        assertThat(result.activeCampaignCount).isEqualTo(1L)
        assertThat(result.nextMilestone).isNotNull
        assertThat(result.nextMilestone!!.title).isEqualTo("Premier palier")
        assertThat(result.nextMilestone.remainingAmount).isEqualByComparingTo("200")
        assertThat(result.avgProgress).isEqualByComparingTo("0.3000")
        assertThat(result.donations6Months).hasSize(6)
        assertThat(result.donations6Months.last().amount).isEqualByComparingTo("1000")
        assertThat(result.recentActivity).isEmpty()
    }

    @Test
    fun `getDashboard when no LIVE campaigns returns neutral values`() {
        every { donationRepository.sumConfirmedAmountByAssociationId(assocId) } returns null
        every { campaignRepository.findAllByAssociationIdAndStatus(assocId, CampaignStatus.LIVE) } returns emptyList()
        every { milestoneRepository.findNextMilestoneByAssociationId(assocId, PageRequest.of(0, 1)) } returns emptyList()
        every { donationRepository.findMonthlyAmountsByAssociationId(assocId, any()) } returns emptyList()
        every { donationRepository.findRecentByAssociationId(assocId, PageRequest.of(0, 10)) } returns emptyList()

        val result = service.getDashboard(assocId)

        assertThat(result.totalRaisedActive).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.activeCampaignCount).isEqualTo(0L)
        assertThat(result.nextMilestone).isNull()
        assertThat(result.avgProgress).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.donations6Months).hasSize(6)
        result.donations6Months.forEach { assertThat(it.amount).isEqualByComparingTo(BigDecimal.ZERO) }
        assertThat(result.recentActivity).isEmpty()
    }

    @Test
    fun `getDashboard fills missing months with zero`() {
        val now = YearMonth.now()
        val currentMonth = now.format(monthFmt)

        every { donationRepository.sumConfirmedAmountByAssociationId(assocId) } returns BigDecimal.ZERO
        every { campaignRepository.findAllByAssociationIdAndStatus(assocId, CampaignStatus.LIVE) } returns emptyList()
        every { milestoneRepository.findNextMilestoneByAssociationId(assocId, PageRequest.of(0, 1)) } returns emptyList()
        // Only current month has data; the other 5 months are absent
        every { donationRepository.findMonthlyAmountsByAssociationId(assocId, any()) } returns
            listOf(monthlyRow(currentMonth, BigDecimal("200")))
        every { donationRepository.findRecentByAssociationId(assocId, PageRequest.of(0, 10)) } returns emptyList()

        val result = service.getDashboard(assocId)

        assertThat(result.donations6Months).hasSize(6)
        result.donations6Months.dropLast(1).forEach { assertThat(it.amount).isEqualByComparingTo(BigDecimal.ZERO) }
        assertThat(result.donations6Months.last().amount).isEqualByComparingTo("200")
    }

    @Test
    fun `getDashboard maps anonymous donor label to Anonyme`() {
        val donor = mockk<org.commonlink.entity.DonorProfile> {
            every { anonymous } returns true
            every { displayName } returns "Alice"
        }
        val donation = mockk<org.commonlink.entity.Donation> {
            every { this@mockk.donor } returns donor
            every { amount } returns BigDecimal("50")
            every { confirmedAt } returns Instant.now()
        }

        every { donationRepository.sumConfirmedAmountByAssociationId(assocId) } returns BigDecimal.ZERO
        every { campaignRepository.findAllByAssociationIdAndStatus(assocId, CampaignStatus.LIVE) } returns emptyList()
        every { milestoneRepository.findNextMilestoneByAssociationId(assocId, PageRequest.of(0, 1)) } returns emptyList()
        every { donationRepository.findMonthlyAmountsByAssociationId(assocId, any()) } returns emptyList()
        every { donationRepository.findRecentByAssociationId(assocId, PageRequest.of(0, 10)) } returns listOf(donation)

        val result = service.getDashboard(assocId)

        assertThat(result.recentActivity).hasSize(1)
        assertThat(result.recentActivity[0].type).isEqualTo(ActivityType.DONATION)
        assertThat(result.recentActivity[0].label).isEqualTo("Anonyme")
    }

    @Test
    fun `getDashboard uses displayName for non-anonymous donor`() {
        val donor = mockk<org.commonlink.entity.DonorProfile> {
            every { anonymous } returns false
            every { displayName } returns "Bob Martin"
        }
        val donation = mockk<org.commonlink.entity.Donation> {
            every { this@mockk.donor } returns donor
            every { amount } returns BigDecimal("100")
            every { confirmedAt } returns Instant.now()
        }

        every { donationRepository.sumConfirmedAmountByAssociationId(assocId) } returns BigDecimal.ZERO
        every { campaignRepository.findAllByAssociationIdAndStatus(assocId, CampaignStatus.LIVE) } returns emptyList()
        every { milestoneRepository.findNextMilestoneByAssociationId(assocId, PageRequest.of(0, 1)) } returns emptyList()
        every { donationRepository.findMonthlyAmountsByAssociationId(assocId, any()) } returns emptyList()
        every { donationRepository.findRecentByAssociationId(assocId, PageRequest.of(0, 10)) } returns listOf(donation)

        val result = service.getDashboard(assocId)

        assertThat(result.recentActivity[0].label).isEqualTo("Bob Martin")
    }

    @Test
    fun `getDashboard avgProgress is zero when campaign has zero goal`() {
        every { donationRepository.sumConfirmedAmountByAssociationId(assocId) } returns BigDecimal.ZERO
        every { campaignRepository.findAllByAssociationIdAndStatus(assocId, CampaignStatus.LIVE) } returns
            listOf(campaign(raised = BigDecimal("100"), goal = BigDecimal.ZERO))
        every { milestoneRepository.findNextMilestoneByAssociationId(assocId, PageRequest.of(0, 1)) } returns emptyList()
        every { donationRepository.findMonthlyAmountsByAssociationId(assocId, any()) } returns emptyList()
        every { donationRepository.findRecentByAssociationId(assocId, PageRequest.of(0, 10)) } returns emptyList()

        val result = service.getDashboard(assocId)

        assertThat(result.avgProgress).isEqualByComparingTo(BigDecimal.ZERO)
    }
}

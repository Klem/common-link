package org.commonlink.service

import org.commonlink.dto.ActivityItemDto
import org.commonlink.dto.ActivityType
import org.commonlink.dto.DashboardStatsDto
import org.commonlink.dto.MonthlyPointDto
import org.commonlink.dto.NextMilestoneDto
import org.commonlink.entity.CampaignStatus
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Computes aggregate dashboard statistics for the association home screen.
 *
 * All DB aggregation is pushed to the repository layer; this service only
 * fills zero-value buckets for months with no donations.
 */
@Service
class AssociationDashboardService(
    private val donationRepository: DonationRepository,
    private val campaignRepository: CampaignRepository,
    private val campaignMilestoneRepository: CampaignMilestoneRepository,
    private val associationProfileRepository: AssociationProfileRepository,
) {

    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    /**
     * Computes the full dashboard payload for the given association.
     *
     * @param associationId UUID of the authenticated association profile.
     */
    fun getDashboard(userId: UUID): DashboardStatsDto {
        val associationId = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!

        val totalRaisedActive =
            donationRepository.sumConfirmedAmountByAssociationId(associationId) ?: BigDecimal.ZERO

        val liveCampaigns =
            campaignRepository.findAllByAssociationIdAndStatus(associationId, CampaignStatus.LIVE)
        val activeCampaignCount = liveCampaigns.size.toLong()

        val nextMilestone = campaignMilestoneRepository
            .findNextMilestoneByAssociationId(associationId, PageRequest.of(0, 1))
            .firstOrNull()
            ?.let { m ->
                NextMilestoneDto(
                    title = m.title,
                    targetAmount = m.targetAmount,
                    remainingAmount = m.targetAmount.subtract(m.campaign.raised),
                )
            }

        val avgProgress = if (liveCampaigns.isEmpty()) {
            BigDecimal.ZERO
        } else {
            val sum = liveCampaigns
                .map { c ->
                    if (c.goal.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
                    else c.raised.divide(c.goal, 4, RoundingMode.HALF_UP)
                }
                .fold(BigDecimal.ZERO, BigDecimal::add)
            sum.divide(BigDecimal(liveCampaigns.size), 4, RoundingMode.HALF_UP)
        }

        // Start of the oldest month in the 6-month window
        val since = YearMonth.now().minusMonths(5).atDay(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant()
        val monthlyRows = donationRepository.findMonthlyAmountsByAssociationId(associationId, since)
        val monthlyMap = monthlyRows.associate { row ->
            "%04d-%02d".format(row.getYear().toInt(), row.getMonth().toInt()) to row.getAmount()
        }
        val donations6Months = buildLast6Months().map { month ->
            MonthlyPointDto(month = month, amount = monthlyMap[month] ?: BigDecimal.ZERO)
        }

        val recentDonations =
            donationRepository.findRecentByAssociationId(associationId, PageRequest.of(0, 10))
        val recentActivity = recentDonations.map { d ->
            ActivityItemDto(
                type = ActivityType.DONATION,
                label = if (d.donor.anonymous) "Anonyme" else d.donor.displayName ?: "Anonyme",
                amount = d.amount,
                occurredAt = d.confirmedAt!!,
            )
        }

        return DashboardStatsDto(
            totalRaisedActive = totalRaisedActive,
            activeCampaignCount = activeCampaignCount,
            nextMilestone = nextMilestone,
            avgProgress = avgProgress,
            donations6Months = donations6Months,
            recentActivity = recentActivity,
        )
    }

    /** Returns the last 6 calendar months as "yyyy-MM" strings, oldest first. */
    private fun buildLast6Months(): List<String> {
        val now = YearMonth.now()
        return (5 downTo 0).map { back -> now.minusMonths(back.toLong()).format(monthFmt) }
    }
}

package org.commonlink.dto

import java.math.BigDecimal
import java.time.Instant

/** Types of events shown in the dashboard activity feed. */
enum class ActivityType { DONATION, MILESTONE_REACHED, PAYMENT }

/** A single event in the dashboard recent-activity feed. */
data class ActivityItemDto(
    val type: ActivityType,
    val label: String,
    val amount: BigDecimal?,
    val occurredAt: Instant,
)

/** Monthly fundraising total for the 6-month bar chart. */
data class MonthlyPointDto(
    /** ISO month string, e.g. "2026-01". */
    val month: String,
    val amount: BigDecimal,
)

/** The campaign milestone closest to its target amount. */
data class NextMilestoneDto(
    val title: String,
    val targetAmount: BigDecimal,
    val remainingAmount: BigDecimal,
)

/** Aggregate statistics returned by GET /api/association/dashboard. */
data class DashboardStatsDto(
    /** Sum of confirmed donations across all currently LIVE campaigns. */
    val totalRaisedActive: BigDecimal,
    val activeCampaignCount: Long,
    /** Null when no unfinished milestone exists across LIVE campaigns. */
    val nextMilestone: NextMilestoneDto?,
    /** Average raised/goal ratio across LIVE campaigns (0.0–1.0, 4 decimal places). */
    val avgProgress: BigDecimal,
    /** Confirmed donation amounts for the last 6 calendar months, oldest first. */
    val donations6Months: List<MonthlyPointDto>,
    /** Last 10 activity events, most recent first. */
    val recentActivity: List<ActivityItemDto>,
)

package org.commonlink.dto

import java.math.BigDecimal

/**
 * Aggregated payout statistics for a campaign, used to populate the Payments tab KPIs.
 *
 * @param confirmedAmount Total amount (EUR) of CONFIRMED payouts.
 * @param confirmedCount Number of CONFIRMED payouts.
 * @param pendingAmount Total amount (EUR) of PENDING payouts.
 * @param txTotal Total number of payouts across all statuses.
 * @param txConfirmed Count of CONFIRMED payouts (same as [confirmedCount], exposed separately for frontend convenience).
 * @param availableBalance Estimated available funds = total confirmed donations - confirmed payouts.
 */
data class PayoutSummaryDto(
    val confirmedAmount: BigDecimal,
    val confirmedCount: Long,
    val pendingAmount: BigDecimal,
    val txTotal: Long,
    val txConfirmed: Long,
    val availableBalance: BigDecimal,
)

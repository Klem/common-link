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
 * @param paymentsEnabled Whether a payout can be issued at all. False only under the prod profile
 *   while Bridge runs in demo mode (`app.bridge.demo-mode`): the transfer would be simulated, never
 *   sent to a bank, yet reported as settled. The Payments tab greys out its submit button on this
 *   flag rather than letting a real association believe it has paid someone. Local and staging stay
 *   enabled — exercising the payout journey without Bridge credentials is what demo mode is for.
 */
data class PayoutSummaryDto(
    val confirmedAmount: BigDecimal,
    val confirmedCount: Long,
    val pendingAmount: BigDecimal,
    val txTotal: Long,
    val txConfirmed: Long,
    val availableBalance: BigDecimal,
    val paymentsEnabled: Boolean,
)

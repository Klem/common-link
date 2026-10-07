package org.commonlink.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Per-payout funding breakdown for the donor's "Voir la répartition" view (Sprint 6) — the inverse
 * of the per-donation traceability view: for one payout, which donations funded it.
 *
 * Privacy: a payout funded by several donors must never expose another donor's exact contribution
 * **as a number this API states**. [myLines] is always full detail — it's the viewing donor's own
 * money. Everyone else is folded into [othersTotal] (the amount), which is withheld (null) below a
 * minimum number of contributing donations. ⚠️ This does not make the amount undiscoverable: the
 * campaign report page already shows the payout's total cost, so `payoutCost - myTotal` always
 * equals the combined total of every other contributor, computable by the donor regardless of this
 * field — see [org.commonlink.service.DonorPayoutBreakdownService] for why that residual can't be
 * closed without removing data the feature legitimately needs.
 *
 * [othersDonationCount] is **always** populated, including `0` — a count carries no monetary
 * information, so it's never withheld. This is what lets the frontend tell "no other donor
 * contributed at all" (`othersDonationCount == 0`, nothing to hide) apart from "some did, but too
 * few to aggregate safely" (`othersDonationCount` in 1..2, `othersTotal == null`) — collapsing both
 * into the same null would misleadingly claim "also funded by others" when that may be false.
 */
data class PayoutFundingBreakdownDto(
    val payoutId: UUID,
    val payoutLabel: String,
    val payoutAmount: BigDecimal,
    val myLines: List<PayoutFundingLineDto>,
    val myTotal: BigDecimal,
    val othersTotal: BigDecimal?,
    val othersDonationCount: Int,
)

/** One of the viewing donor's own donations that helped fund a payout. */
data class PayoutFundingLineDto(
    val donationId: UUID,
    val confirmedAt: Instant,
    val amount: BigDecimal,
)

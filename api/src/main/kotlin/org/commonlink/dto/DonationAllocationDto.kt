package org.commonlink.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Result of the FIFO allocation of one confirmed donation against a campaign's confirmed payouts.
 *
 * @property usedAmount portion of [org.commonlink.entity.Donation.amount] consumed by confirmed payouts so far.
 * @property remainingAmount portion not yet consumed by any confirmed payout.
 */
data class DonationAllocationDto(
    val donationId: UUID,
    val usedAmount: BigDecimal,
    val remainingAmount: BigDecimal,
    val fundedPayouts: List<FundedPayoutShareDto>,
)

/** Portion of one payout funded by a given donation. */
data class FundedPayoutShareDto(
    val payoutId: UUID,
    val label: String,
    val amountImputed: BigDecimal,
    val confirmedAt: Instant,
)

package org.commonlink.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Step of the donor-facing "parcours de votre don" timeline. */
enum class JourneyStep { RECEIVED, RECORDED, SPENT, IMPACT_REPORTED }

/** One step of [DonorDonationJourneyDto.steps]. */
data class JourneyStepDto(
    val step: JourneyStep,
    val reached: Boolean,
    val reachedAt: Instant?,
)

/**
 * The 4-step journey of one donation, for the "parcours de votre don" timeline and the
 * traceability modal.
 *
 * @property previousDonationId the donor's chronologically earlier donation, for "précédent" navigation.
 * @property nextDonationId the donor's chronologically later donation, for "suivant" navigation.
 * @property usedAmount portion of the donation already allocated (FIFO) to a confirmed payout — same
 *   value as [DonationAllocationDto.usedAmount] for this donation, repeated here so the traceability
 *   modal is self-sufficient from a single call.
 * @property remainingAmount portion not yet allocated to any confirmed payout.
 * @property fundedPayouts confirmed payouts this donation (partially) funded — the modal's "dépenses financées".
 */
data class DonorDonationJourneyDto(
    val donationId: UUID,
    val steps: List<JourneyStepDto>,
    val previousDonationId: UUID?,
    val nextDonationId: UUID?,
    val usedAmount: BigDecimal,
    val remainingAmount: BigDecimal,
    val fundedPayouts: List<FundedPayoutShareDto>,
)

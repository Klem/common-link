package org.commonlink.service

import org.commonlink.entity.FundsState
import org.commonlink.exception.FundsNotAvailableException
import org.commonlink.repository.DonationRepository
import java.math.BigDecimal
import java.util.UUID

/**
 * Builds the coded payout refusal of [campaignId] ([FundsNotAvailableException]): [disbursable] (clamped at zero)
 * plus the campaign's HelloAsso donations not arrived yet — paid out but within the settlement delay (`inTransit`)
 * and still held by HelloAsso, payout in preparation included (`atProvider`). Shared by the creation
 * ([PayoutService]) and confirmation ([PayoutConfirmer]) checks, so both refusals carry the same figures.
 */
internal fun DonationRepository.fundsNotAvailable(campaignId: UUID, disbursable: BigDecimal): FundsNotAvailableException {
    val byState = sumConfirmedAmountsByCampaignIdGroupedByFundsState(campaignId).associate { it.state to it.amount }
    fun sum(vararg states: FundsState) = states.fold(BigDecimal.ZERO) { acc, s -> acc + (byState[s] ?: BigDecimal.ZERO) }
    return FundsNotAvailableException(
        disbursable = disbursable.max(BigDecimal.ZERO),
        inTransit = sum(FundsState.IN_TRANSIT),
        atProvider = sum(FundsState.AT_PROVIDER, FundsState.PAYOUT_PREPARING),
    )
}

package org.commonlink.service

import org.commonlink.dto.DonationAllocationDto
import org.commonlink.dto.FundedPayoutShareDto
import org.commonlink.entity.PayoutStatus
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayoutRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/**
 * Computes, for a campaign, which confirmed donations funded which confirmed payouts — strict
 * FIFO by confirmation time, across all donors of the campaign.
 *
 * Only CONFIRMED payouts consume donations: "used" means spent, never committed. A PENDING payout
 * reserves its amount on the association's available balance
 * ([PayoutService.getSummary]) but takes no donation here — so a payout confirmed while an older
 * one is still pending consumes the oldest donations, and the pending one, once confirmed, the next
 * ones. Both lists only ever grow at the end, so an allocation already shown to a donor never
 * changes afterwards; FAILED and CANCELLED payouts never enter it.
 *
 * Pure computation, no persistence: recomputed on every call. Not scoped by donor — callers
 * enforce donor read scope before invoking this (see [DonorDonationJourneyService], the
 * donor-scoped campaign report) since the allocation itself must see every donor's donations to
 * be correct.
 */
@Service
class DonationAllocationService(
    private val donationRepository: DonationRepository,
    private val payoutRepository: PayoutRepository,
) {

    /**
     * Allocates all confirmed payouts of [campaignId] against its confirmed donations, oldest
     * donation first, oldest payout first. A payout may span several donations; a donation may
     * fund several payouts (partially).
     *
     * @return one entry per confirmed donation of the campaign, in confirmedAt order.
     */
    fun allocateCampaign(campaignId: UUID): List<DonationAllocationDto> {
        val donations = donationRepository.findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAscIdAsc(campaignId)
        val payouts = payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAscIdAsc(campaignId, PayoutStatus.CONFIRMED)

        val remaining = donations.associateTo(LinkedHashMap()) { it.id!! to it.amount }
        val fundedPayouts = donations.associateTo(LinkedHashMap()) { it.id!! to mutableListOf<FundedPayoutShareDto>() }

        // Two cursors over lists already sorted ASC by confirmedAt: donationIndex only ever moves
        // forward, so once a donation is exhausted it is never revisited by a later payout.
        var donationIndex = 0
        for (payout in payouts) {
            var toImpute = payout.amount
            while (toImpute > BigDecimal.ZERO && donationIndex < donations.size) {
                val donationId = donations[donationIndex].id!!
                val available = remaining.getValue(donationId)
                if (available <= BigDecimal.ZERO) {
                    donationIndex++
                    continue
                }
                val imputed = minOf(available, toImpute)
                remaining[donationId] = available - imputed
                toImpute -= imputed
                fundedPayouts.getValue(donationId).add(
                    FundedPayoutShareDto(
                        payoutId = payout.id,
                        label = payout.label,
                        amountImputed = imputed,
                        confirmedAt = payout.confirmedAt!!,
                    )
                )
            }
        }

        return donations.map { donation ->
            val donationId = donation.id!!
            DonationAllocationDto(
                donationId = donationId,
                usedAmount = donation.amount - remaining.getValue(donationId),
                remainingAmount = remaining.getValue(donationId),
                fundedPayouts = fundedPayouts.getValue(donationId),
            )
        }
    }
}

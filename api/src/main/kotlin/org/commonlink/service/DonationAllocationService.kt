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
 * Computes, for a campaign, which available donations funded which confirmed payouts — strict
 * FIFO by availability time ([org.commonlink.entity.Donation.fundsAvailableAt], then confirmation
 * time, then id), across all donors of the campaign. A donation is available once its funds have
 * reached the association's bank account: at confirmation for every provider but HelloAsso, whose
 * confirmed donations stay out of the allocation until paid out to the association.
 *
 * Only CONFIRMED payouts consume donations: "used" means spent, never committed. A PENDING payout
 * reserves its amount on the association's available balance
 * ([PayoutService.getSummary]) but takes no donation here — so a payout confirmed while an older
 * one is still pending consumes the oldest donations, and the pending one, once confirmed, the next
 * ones. Payouts only ever join at the end; FAILED and CANCELLED payouts never enter it. Donations
 * almost always join at the end too. A HelloAsso donation becomes available when it is paid out, so
 * it always joins at the end (append-only). A Mollie donation confirmed late (delayed notification,
 * reconciler) is available at the provider's payment time and may sort before donations confirmed in
 * between, which shifts the allocation already shown for those donations.
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
     * Allocates all confirmed payouts of [campaignId] against its available donations, earliest
     * available donation first, oldest payout first. A payout may span several donations; a donation
     * may fund several payouts (partially).
     *
     * @return one entry per available donation of the campaign, in (fundsAvailableAt, confirmedAt, id)
     *   order. A confirmed donation not yet available has no entry: callers treat it as unused.
     */
    fun allocateCampaign(campaignId: UUID): List<DonationAllocationDto> {
        val donations = donationRepository
            .findByCampaignIdAndFundsAvailableAtIsNotNullOrderByFundsAvailableAtAscConfirmedAtAscIdAsc(campaignId)
        val payouts = payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAscIdAsc(campaignId, PayoutStatus.CONFIRMED)

        val remaining = donations.associateTo(LinkedHashMap()) { it.id!! to it.amount }
        val fundedPayouts = donations.associateTo(LinkedHashMap()) { it.id!! to mutableListOf<FundedPayoutShareDto>() }

        // Two cursors over lists already sorted ASC (donations by availability, payouts by confirmedAt): donationIndex only ever moves
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

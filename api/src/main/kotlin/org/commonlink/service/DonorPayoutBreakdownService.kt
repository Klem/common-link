package org.commonlink.service

import org.commonlink.dto.PayoutFundingBreakdownDto
import org.commonlink.dto.PayoutFundingLineDto
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Minimum number of OTHER donors' donations contributing to a payout before their aggregate total
 * is shown at all — decided 01/10/2026, see `.tasks/todo.md` Sprint 6 §6.
 *
 * ⚠️ **This stops the app itself from ever stating another donor's amount — it does not make that
 * amount undiscoverable.** `payoutAmount` (the expense's total cost) is already shown unconditionally
 * elsewhere on the same campaign report page (Sprint 2, core transparency data, not something this
 * floor can hide without breaking that feature), and [PayoutFundingBreakdownDto.myTotal] is the
 * viewing donor's own money, also necessarily shown. `payoutAmount - myTotal` always equals the
 * combined total of every other contributor, computable by the donor themselves regardless of this
 * floor — and at exactly one other contributing donation, that combined total **is** that donor's
 * exact amount. No code change here can close this without removing one of those two legitimate,
 * independently-necessary numbers. Flagged to the user 01/10/2026 as a residual, accepted risk —
 * not something to "fix" by further restricting [PayoutFundingBreakdownDto.myTotal] or the expense
 * totals shown on the report page.
 */
private const val MIN_OTHER_DONATIONS_TO_AGGREGATE = 3

/**
 * Read-only service backing the donor's "Voir la répartition" view on a campaign's payouts
 * (Sprint 6) — the inverse of [DonationAllocationService.allocateCampaign]'s usual per-donation
 * view: for one payout, which donations funded it.
 *
 * [DonationAllocationService.allocateCampaign] necessarily computes every donor's donations to stay
 * FIFO-correct across the whole campaign; this service is where that gets partitioned by donor
 * before anything crosses the donor boundary. Never return another donor's donationId or exact
 * amount directly — see [MIN_OTHER_DONATIONS_TO_AGGREGATE] for what this guarantee does and does
 * not cover.
 */
@Service
class DonorPayoutBreakdownService(
    private val donorReadScope: DonorReadScope,
    private val payoutRepository: PayoutRepository,
    private val donationRepository: DonationRepository,
    private val donationAllocationService: DonationAllocationService,
) {

    /**
     * @param userId UUID of the authenticated user.
     * @throws NotFoundException if [campaignId] or [payoutId] does not exist.
     * @throws AccessDeniedException if the donor has no confirmed donation on [campaignId], or if
     *   [payoutId] does not belong to [campaignId].
     */
    @Transactional(readOnly = true)
    fun getBreakdown(userId: UUID, campaignId: UUID, payoutId: UUID): PayoutFundingBreakdownDto {
        val donorId = donorReadScope.resolve(userId).id!!
        donorReadScope.assertHasDonatedTo(donorId, campaignId)

        val payout = payoutRepository.findById(payoutId)
            .orElseThrow { NotFoundException("Payout not found: $payoutId") }
        if (payout.campaign.id != campaignId) {
            throw AccessDeniedException("Payout $payoutId does not belong to campaign $campaignId")
        }

        // Reshape of allocateCampaign's already-computed result: every donation's share of THIS
        // payout, regardless of donor — partitioned below, never returned as-is.
        val shares = donationAllocationService.allocateCampaign(campaignId)
            .mapNotNull { allocation ->
                val share = allocation.fundedPayouts.firstOrNull { it.payoutId == payoutId }
                    ?: return@mapNotNull null
                allocation.donationId to share.amountImputed
            }
        // Bounded by how many donations contribute to ONE payout -- small, a single batch query.
        val donationsById = donationRepository.findAllById(shares.map { it.first }).associateBy { it.id }

        val myLines = mutableListOf<PayoutFundingLineDto>()
        var othersTotal = BigDecimal.ZERO
        var othersCount = 0
        for ((donationId, amount) in shares) {
            val donation = donationsById.getValue(donationId)
            if (donation.donor.id == donorId) {
                myLines.add(PayoutFundingLineDto(donationId, donation.confirmedAt!!, amount))
            } else {
                othersTotal += amount
                othersCount++
            }
        }
        return PayoutFundingBreakdownDto(
            payoutId = payoutId,
            payoutLabel = payout.label,
            payoutAmount = payout.amount,
            myLines = myLines.sortedByDescending { it.confirmedAt },
            myTotal = myLines.sumOf { it.amount },
            // Null means "hidden for privacy" (1-2 others), never "none" -- othersDonationCount
            // disambiguates: 0 there means genuinely no other donor, independent of this field.
            othersTotal = if (othersCount >= MIN_OTHER_DONATIONS_TO_AGGREGATE) othersTotal else null,
            othersDonationCount = othersCount,
        )
    }
}

package org.commonlink.service

import org.commonlink.config.DonationCapProperties
import org.commonlink.entity.Campaign
import org.commonlink.exception.CollectionCapExceededException
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Enforces the collection cap of a campaign: how much it may still take in.
 *
 * The rule the cap exists to satisfy is negative — **a donation is never refunded because the
 * campaign collected too much.** Once a payment is captured, the fiscal receipt is numbered, its
 * keccak256 is written on-chain and the PDF is emailed; unwinding that is not a normal operation.
 * The only place where refusing is still free is before the payment is initiated, which is where
 * [requireWithinCap] is called from.
 *
 * Three quantities make up the cap arithmetic:
 * - [Campaign.goal] × (1 + margin) — the ceiling, see [DonationCapProperties.marginPercent];
 * - [Campaign.raised] — what is already confirmed;
 * - the amounts held by payment sessions still open, so two simultaneous checkouts cannot each be
 *   told the full remaining capacity is theirs.
 */
@Service
class DonationCapService(
    private val donationRepository: DonationRepository,
    private val properties: DonationCapProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Ceiling a campaign may collect: `goal × (1 + marginPercent / 100)`, rounded to the cent.
     *
     * A margin of `0` makes the cap the goal exactly.
     */
    fun cap(campaign: Campaign): BigDecimal =
        campaign.goal
            .multiply(BigDecimal.ONE + properties.marginPercent.divide(HUNDRED))
            .setScale(2, RoundingMode.HALF_UP)

    /**
     * Amount [campaign] may still accept: cap minus confirmed donations minus amounts reserved by
     * open payment sessions. Never negative — a campaign already past its cap has zero capacity,
     * not a debt.
     */
    fun remainingCapacity(campaign: Campaign): BigDecimal {
        val reserved = reservedAmount(campaign)
        val remaining = cap(campaign) - campaign.raised - reserved
        return remaining.max(BigDecimal.ZERO)
    }

    /**
     * Refuses [amount] when it would take [campaign] past its cap, or when the campaign already
     * carries as many open payment sessions as [DonationCapProperties.maxPendingSessions] allows.
     *
     * Called before the payment is created with the provider — after that point a payable checkout
     * URL exists and refusing is no longer free.
     *
     * The session ceiling exists because a pending row *is* a reservation and the widget endpoint is
     * unauthenticated: without it, an outsider creates sessions until the remaining capacity is
     * exhausted and every genuine donor is refused for the whole reservation TTL
     * (security audit 2026-08-20, M6). Refusing on the ceiling is reported as a cap event with zero
     * remaining capacity, so the donor sees "not accepting donations right now" rather than a hint
     * that the platform is being probed.
     *
     * @throws CollectionCapExceededException carrying the still-acceptable amount.
     */
    fun requireWithinCap(campaign: Campaign, amount: BigDecimal) {
        val pendingSessions = donationRepository.countPendingByCampaignIdSince(
            campaign.id!!,
            Instant.now().minus(properties.reservationTtl),
        )
        if (pendingSessions >= properties.maxPendingSessions) {
            logger.warn(
                "Donation refused on pending-session ceiling: campaign={} pending={} max={}",
                campaign.id, pendingSessions, properties.maxPendingSessions,
            )
            throw CollectionCapExceededException(BigDecimal.ZERO)
        }

        val remaining = remainingCapacity(campaign)
        if (amount > remaining) {
            logger.info(
                "Donation refused on cap: campaign={} requested={} remaining={} cap={} raised={}",
                campaign.id, amount, remaining, cap(campaign), campaign.raised,
            )
            throw CollectionCapExceededException(remaining)
        }
    }

    /**
     * Amount held by payment sessions started on [campaign] and not yet confirmed.
     *
     * A pending donation row *is* the reservation — there is no reservation column — and
     * [DonationCapProperties.reservationTtl] bounds how long an abandoned checkout keeps holding
     * capacity.
     */
    private fun reservedAmount(campaign: Campaign): BigDecimal {
        val since = Instant.now().minus(properties.reservationTtl)
        return donationRepository.sumPendingAmountByCampaignIdSince(campaign.id!!, since) ?: BigDecimal.ZERO
    }

    private companion object {
        val HUNDRED: BigDecimal = BigDecimal("100")
    }
}

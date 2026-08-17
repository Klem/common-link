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
     * Refuses [amount] when it would take [campaign] past its cap.
     *
     * Called before the payment is created with the provider — after that point a payable checkout
     * URL exists and refusing is no longer free.
     *
     * @throws CollectionCapExceededException carrying the still-acceptable amount.
     */
    fun requireWithinCap(campaign: Campaign, amount: BigDecimal) {
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

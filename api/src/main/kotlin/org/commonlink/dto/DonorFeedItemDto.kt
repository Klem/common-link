package org.commonlink.dto

import java.time.Instant
import java.util.UUID

/** Kind of event surfaced by the donor engagement feed (see [DonorFeedItemDto]). */
enum class DonorFeedItemType {
    /** A payout was confirmed on an association the donor supports. */
    PAYOUT_CONFIRMED,
    /** A milestone was reached on a campaign the donor funded. */
    MILESTONE_REACHED,
    /** A campaign the donor funded reached its goal and was completed. */
    CAMPAIGN_COMPLETED,
}

/**
 * One event of the donor's "Depuis votre dernière visite" home block.
 *
 * @property label Fully-resolved sentence, built server-side so no screen can improvise a wording
 *   that over-promises -- a [DonorFeedItemType.PAYOUT_CONFIRMED] item always uses the D2-validated
 *   sentence, never claiming the expense was registered on-chain
 *   (see `docs/legal/registre-onchain-des-depenses.md`).
 */
data class DonorFeedItemDto(
    val type: DonorFeedItemType,
    val campaignId: UUID,
    val campaignName: String,
    val associationName: String,
    val occurredAt: Instant,
    val label: String,
)

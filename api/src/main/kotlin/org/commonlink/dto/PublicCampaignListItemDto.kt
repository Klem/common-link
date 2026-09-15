package org.commonlink.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Row projection loaded by
 * [org.commonlink.repository.CampaignRepository.findPublicLive].
 *
 * Not serialized — internal hop between the JPQL constructor expression and
 * [PublicCampaignListItemDto]. It exists only because the public donation URL is built from the
 * configured `app.frontend-url`, which JPQL cannot know; every other field is read straight from
 * the database in a single bounded query.
 *
 * @param campaignId UUID of the campaign (already public: it is the cover-image URL segment).
 * @param campaignName Display name of the campaign.
 * @param campaignEmoji Visual icon of the campaign, used as placeholder when no cover image is set.
 * @param campaignCategory Free-text category set by the association, or null.
 * @param coverImage Public serving path of the cover image, or null if none was uploaded.
 * @param campaignUpdatedAt Timestamp of the campaign's last modification — used as a
 *   cache-busting version token for [coverImage], which is served from a stable, publicly
 *   cached URL (see [PublicCampaignListItemDto.campaignUpdatedAt]).
 * @param goal Total fundraising goal in euros.
 * @param raised Amount raised so far in euros.
 * @param milestoneCount Number of milestones defined for this campaign.
 * @param associationName Official registered name of the owning association.
 * @param associationLogo Public serving path of the association landing logo, or null.
 * @param widgetToken Opaque public widget token, used to build the donation URL.
 */
data class PublicCampaignRow(
    val campaignId: UUID,
    val campaignName: String,
    val campaignEmoji: String,
    val campaignCategory: String?,
    val coverImage: String?,
    val campaignUpdatedAt: Instant,
    val goal: BigDecimal,
    val raised: BigDecimal,
    val milestoneCount: Int,
    val associationName: String,
    val associationLogo: String?,
    val widgetToken: String,
)

/**
 * Public projection of a live campaign in the landing-page directory (`/projets`).
 *
 * Intentionally minimal, on the same principle as [PublicWidgetDto] — exposes only what a campaign
 * card renders. No lifecycle [org.commonlink.entity.CampaignStatus], no `createdAt`, no association
 * UUID, no raw widget token: the token is folded into [donationUrl], which is the only thing the
 * card does with it.
 *
 * [CampaignSummaryDto] is deliberately not reused here — it carries the internal status and
 * creation timestamp of the association dashboard.
 *
 * @param campaignId UUID of the campaign; segment of the public cover-image URL.
 * @param campaignName Display name shown as the card title.
 * @param campaignEmoji Visual icon, rendered as placeholder when [coverImage] is null.
 * @param campaignCategory Free-text category badge, or null when the association set none.
 * @param coverImage Public serving path of the cover image (`/api/public/campaigns/{id}/cover`),
 *   or null. Never call that URL when this is null — it answers 404, not a placeholder.
 * @param campaignUpdatedAt Timestamp of the campaign's last modification. The cover-image URL is
 *   stable (campaign id only) and served with a 5-minute public cache, so the frontend must append
 *   this as a cache-busting version token rather than requesting [coverImage] as-is.
 * @param goal Total fundraising goal in euros.
 * @param raised Amount raised so far in euros.
 * @param milestoneCount Number of milestones, shown in the card footer.
 * @param associationName Name of the association collecting the donations.
 * @param associationLogo Public serving path of the association logo
 *   (`/api/public/associations/{id}/logo`), or null. Same 404 caveat as [coverImage].
 * @param donationUrl Absolute URL of the public donation landing page for this campaign, built on
 *   the configured `app.frontend-url`.
 */
data class PublicCampaignListItemDto(
    val campaignId: UUID,
    val campaignName: String,
    val campaignEmoji: String,
    val campaignCategory: String?,
    val coverImage: String?,
    val campaignUpdatedAt: Instant,
    val goal: BigDecimal,
    val raised: BigDecimal,
    val milestoneCount: Int,
    val associationName: String,
    val associationLogo: String?,
    val donationUrl: String,
)

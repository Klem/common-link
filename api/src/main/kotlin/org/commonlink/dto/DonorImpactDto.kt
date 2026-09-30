package org.commonlink.dto

import java.util.UUID

/** One campaign card of the "Impact de mes dons" gallery. */
data class DonorImpactDto(
    val campaignId: UUID,
    val campaignName: String,
    val campaignEmoji: String,
    val associationName: String,
    /** Cause -- Campaign.category, used as the filter facet. Null when the campaign carries none. */
    val category: String?,
    /** Free-text impact description written by the association at campaign creation. */
    val impactGoals: String?,
    /**
     * Published story's plain-text summary, null if none or still draft. Never the rich-text
     * [org.commonlink.entity.CampaignStory.storyText] HTML -- the gallery composes this into the
     * collective wording (D6) as plain text, never as markup.
     */
    val storySummary: String?,
)

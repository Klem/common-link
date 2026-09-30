package org.commonlink.dto

import java.math.BigDecimal
import java.util.UUID

/**
 * One recommended campaign for "Projets recommandés" and the home "Pour vous" block (D8, option A).
 *
 * Built directly from [org.commonlink.repository.CampaignRepository.findPublicLive] -- the same
 * eligible set the public directory serves -- so [donationUrl] is always non-null.
 *
 * @property matchedCategory Non-null when the recommendation is explained by a cause the donor
 *   already funds -- shown to the donor so the criterion stays explicable (D8 is an open question
 *   this sprint doesn't fully resolve; see the master plan).
 */
data class DonorRecommendationDto(
    val campaignId: UUID,
    val campaignName: String,
    val campaignEmoji: String,
    val associationName: String,
    val category: String?,
    val coverImage: String?,
    val goal: BigDecimal,
    val raised: BigDecimal,
    val donationUrl: String,
    val matchedCategory: String?,
)

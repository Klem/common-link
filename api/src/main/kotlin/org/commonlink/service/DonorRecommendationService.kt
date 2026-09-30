package org.commonlink.service

import org.commonlink.dto.DonorRecommendationDto
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.security.DonorReadScope
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read-only service backing "Projets recommandés" and the home "Pour vous" block (L14).
 *
 * Simple rule (D8, option A): rank [CampaignRepository.findPublicLive]'s rows by whether their
 * category matches one of the donor's own most-funded categories, excluding associations the donor
 * already supports -- this is meant to surface something new, not repeat "Mes associations". Falls
 * back to the most recent live campaigns when the donor has no category history yet, or when no
 * live campaign matches.
 *
 * No extra eligibility check is needed here: [CampaignRepository.findPublicLive] already restricts
 * its result to campaigns whose association has a reachable widget (see
 * [org.commonlink.entity.AssociationProfile.hasEligibleWidget] -- the same predicate, inlined in
 * that query). This is also why a single association can never be recommended for two different
 * campaigns: only its current [org.commonlink.entity.AssociationProfile.widgetDestinationCampaign]
 * is ever eligible.
 */
@Service
class DonorRecommendationService(
    private val donorReadScope: DonorReadScope,
    private val donationRepository: DonationRepository,
    private val campaignRepository: CampaignRepository,
    private val publicCampaignDirectoryService: PublicCampaignDirectoryService,
) {

    /** @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile. */
    @Transactional(readOnly = true)
    fun getRecommendations(userId: UUID): List<DonorRecommendationDto> {
        val donorId = donorReadScope.resolve(userId).id!!

        val history = donationRepository.findAssociationCategoriesByDonorId(donorId)
        val supportedAssociationIds = history.map { it.getAssociationId() }.toSet()
        val supportedCategories = history.mapNotNull { it.getCategory() }.toSet()

        val eligibleRows = campaignRepository.findPublicLive(PageRequest.of(0, MAX_CANDIDATES))
            .filterNot { it.associationId in supportedAssociationIds }

        // Rows already arrive createdAt DESC (query order) -- category matches are stably sorted
        // ahead of the rest, so "most recent" remains the tie-break within and across both groups.
        val ranked = eligibleRows.sortedByDescending { it.campaignCategory in supportedCategories }

        return ranked.take(MAX_RECOMMENDATIONS).map { row ->
            val matchedCategory = row.campaignCategory?.takeIf { it in supportedCategories }
            DonorRecommendationDto(
                campaignId = row.campaignId,
                campaignName = row.campaignName,
                campaignEmoji = row.campaignEmoji,
                associationName = row.associationName,
                category = row.campaignCategory,
                coverImage = row.coverImage,
                goal = row.goal,
                raised = row.raised,
                donationUrl = publicCampaignDirectoryService.buildDonationUrl(row.widgetToken),
                matchedCategory = matchedCategory,
            )
        }
    }

    private companion object {
        /** Same bound as [org.commonlink.service.PublicCampaignDirectoryService.listLive]. */
        const val MAX_CANDIDATES = 60
        const val MAX_RECOMMENDATIONS = 6
    }
}

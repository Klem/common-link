package org.commonlink.service

import org.commonlink.dto.DonorImpactDto
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.security.DonorReadScope
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read-only service backing the donor's "Impact de mes dons" gallery.
 *
 * Every figure is project-level, never a donor share (D6, option A): the collective wording
 * ("Ce projet a... Vous y avez contribué.") is built entirely from [org.commonlink.entity.Campaign.impactGoals]
 * and the published [org.commonlink.entity.CampaignStory] -- no per-donor amount is computed or
 * displayed here.
 */
@Service
class DonorImpactService(
    private val donorReadScope: DonorReadScope,
    private val donationRepository: DonationRepository,
    private val campaignStoryService: CampaignStoryService,
    private val associationProfileRepository: AssociationProfileRepository,
    private val publicCampaignDirectoryService: PublicCampaignDirectoryService,
) {

    /** @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile. */
    @Transactional(readOnly = true)
    fun listImpacts(userId: UUID): List<DonorImpactDto> {
        val donorId = donorReadScope.resolve(userId).id!!
        return donationRepository.findDistinctCampaignsByDonorId(donorId).map { row ->
            DonorImpactDto(
                campaignId = row.getCampaignId(),
                campaignName = row.getCampaignName(),
                campaignEmoji = row.getCampaignEmoji(),
                associationName = row.getAssociationName(),
                category = row.getCategory(),
                impactGoals = row.getImpactGoals(),
                storySummary = campaignStoryService.getPublishedStory(row.getCampaignId())?.storySummary,
                donationUrl = resolveDonationUrl(row.getAssociationId()),
            )
        }
    }

    /**
     * Absolute donation URL for [associationId], or null when its widget isn't currently reachable
     * (see [org.commonlink.entity.AssociationProfile.hasEligibleWidget]). Points at the
     * association's current live campaign, not necessarily this gallery card's campaign — see the
     * class doc on [org.commonlink.service.DonorRecommendationService] for why.
     */
    private fun resolveDonationUrl(associationId: UUID): String? {
        val association = associationProfileRepository.findById(associationId).orElse(null) ?: return null
        if (!association.hasEligibleWidget()) return null
        return publicCampaignDirectoryService.buildDonationUrl(association.widgetToken!!)
    }
}

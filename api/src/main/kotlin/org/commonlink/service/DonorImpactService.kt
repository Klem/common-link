package org.commonlink.service

import org.commonlink.dto.DonorImpactDto
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
            )
        }
    }
}

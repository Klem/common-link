package org.commonlink.service

import org.commonlink.dto.CampaignStoryDto
import org.commonlink.dto.toDto
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStory
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.CampaignStoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Manages a campaign's impact story (D5, option C): association-side authoring, donor-side read
 * once published.
 */
@Service
class CampaignStoryService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val campaignRepository: CampaignRepository,
    private val campaignStoryRepository: CampaignStoryRepository,
) {

    /**
     * Creates or replaces the story of [campaignId], owned by the authenticated association.
     * Upsert: a second call overwrites [CampaignStory.storyText] in place (no version history).
     *
     * [publish] is one-directional: `true` sets [CampaignStory.publishedAt] if it was null,
     * `false` **never clears an existing [CampaignStory.publishedAt]** -- there is no unpublish
     * action this sprint. This matters because the front-end autosaves the text field on a debounce
     * and will call this endpoint with `publish = false` on every keystroke batch; without this
     * rule that autosave would silently unpublish an already-live story.
     *
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign does not exist under this association -- mirrors
     *   [CampaignService.updateCampaign]'s resolution exactly, never a 403 (an association never
     *   learns whether a campaign id exists under someone else's account).
     */
    @Transactional
    fun upsertStory(userId: UUID, campaignId: UUID, storyText: String, publish: Boolean): CampaignStoryDto {
        val campaign = resolveOwnedCampaign(userId, campaignId)

        val story = campaignStoryRepository.findByCampaignId(campaignId)
            ?: CampaignStory(campaign = campaign, storyText = storyText)
        story.storyText = storyText
        story.updatedAt = Instant.now()
        if (publish && story.publishedAt == null) {
            story.publishedAt = Instant.now()
        }

        return campaignStoryRepository.save(story).toDto()
    }

    /**
     * The association's own view of its story -- draft or published, unlike [getPublishedStory].
     * Backs the campaign editor's story tab, which must show a draft back to its author.
     *
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign does not exist under this association.
     */
    @Transactional(readOnly = true)
    fun getOwnStory(userId: UUID, campaignId: UUID): CampaignStoryDto? {
        resolveOwnedCampaign(userId, campaignId)
        return campaignStoryRepository.findByCampaignId(campaignId)?.toDto()
    }

    /** Read path used by [DonorCampaignReportService] -- null when no story, or unpublished. */
    @Transactional(readOnly = true)
    fun getPublishedStory(campaignId: UUID): CampaignStoryDto? =
        campaignStoryRepository.findByCampaignId(campaignId)
            ?.takeIf { it.publishedAt != null }
            ?.toDto()

    /**
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign does not exist under this association -- mirrors
     *   [CampaignService.updateCampaign]'s resolution exactly, never a 403 (an association never
     *   learns whether a campaign id exists under someone else's account).
     */
    private fun resolveOwnedCampaign(userId: UUID, campaignId: UUID): Campaign {
        val associationId = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!
        return campaignRepository.findByIdAndAssociationId(campaignId, associationId)
            .orElseThrow { NotFoundException("Campaign not found") }
    }
}

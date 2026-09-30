package org.commonlink.service

import org.commonlink.dto.CampaignStoryDto
import org.commonlink.dto.CampaignStoryImageDto
import org.commonlink.dto.toDto
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStory
import org.commonlink.entity.CampaignStoryImage
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.CampaignStoryImageRepository
import org.commonlink.repository.CampaignStoryRepository
import org.commonlink.util.FileTypeSniffer
import org.commonlink.util.StoryHtmlSanitizer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

private const val MAX_STORY_IMAGE_SIZE = 5L * 1024 * 1024
private val STORY_IMAGE_ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")

/** Public serving path of one story image; what the editor inserts as `<img src>`. */
private fun storyImagePath(campaignId: UUID, imageId: UUID): String =
    "/api/public/campaigns/$campaignId/story-images/$imageId"

/**
 * Manages a campaign's impact story (D5, option C): association-side authoring, donor-side read
 * once published.
 */
@Service
class CampaignStoryService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val campaignRepository: CampaignRepository,
    private val campaignStoryRepository: CampaignStoryRepository,
    private val campaignStoryImageRepository: CampaignStoryImageRepository,
) {

    /**
     * Creates or replaces the story of [campaignId], owned by the authenticated association.
     * Upsert: a second call overwrites [CampaignStory.storyText]/[CampaignStory.storySummary] in
     * place (no version history).
     *
     * [storyText] is sanitized here, before it ever reaches the entity -- see
     * [StoryHtmlSanitizer]. [storySummary] is stored as plain text as-is: it is never rendered as
     * HTML, so it needs no sanitization, only the `@NotBlank @Size(max=220)` already enforced on
     * [org.commonlink.dto.UpsertCampaignStoryRequest].
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
    fun upsertStory(
        userId: UUID,
        campaignId: UUID,
        storyText: String,
        storySummary: String,
        publish: Boolean,
    ): CampaignStoryDto {
        val campaign = resolveOwnedCampaign(userId, campaignId)
        val sanitized = StoryHtmlSanitizer.sanitize(storyText)

        val story = campaignStoryRepository.findByCampaignId(campaignId)
            ?: CampaignStory(campaign = campaign, storyText = sanitized, storySummary = storySummary)
        story.storyText = sanitized
        story.storySummary = storySummary
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
     * Stores an image to be embedded in the story of [campaignId], for the rich-text editor to
     * reference by URL.
     *
     * Mirrors [CampaignService.uploadCoverImage]'s validation exactly (JPEG/PNG/WebP, 5 MB) --
     * same frontend upload zone constraints, same reasoning.
     *
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign does not exist under this association.
     * @throws UnprocessableEntityException if the file is empty, too large, or not an accepted image type.
     */
    @Transactional
    fun uploadStoryImage(userId: UUID, campaignId: UUID, file: MultipartFile): CampaignStoryImageDto {
        resolveOwnedCampaign(userId, campaignId)
        validateStoryImage(file)

        val saved = campaignStoryImageRepository.save(
            CampaignStoryImage(
                campaignId = campaignId,
                data = file.bytes,
                contentType = file.contentType!!,
                sizeBytes = file.size,
            )
        )
        val id = saved.id!!
        return CampaignStoryImageDto(id = id.toString(), url = storyImagePath(campaignId, id))
    }

    /**
     * Returns the raw bytes of one story image, for the unauthenticated serving endpoint.
     *
     * Not scoped to a campaign beyond matching [imageId] to it -- an `<img>` tag carries no
     * Bearer token, same reasoning as [CampaignService.getCoverImage].
     *
     * @throws NotFoundException if no such image exists for this campaign.
     */
    @Transactional(readOnly = true)
    fun getStoryImage(campaignId: UUID, imageId: UUID): Pair<String, ByteArray> {
        val image = campaignStoryImageRepository.findById(imageId)
            .filter { it.campaignId == campaignId }
            .orElseThrow { NotFoundException("No story image $imageId for campaign $campaignId") }
        // Served Content-Type comes from the bytes, not the stored declaration (security audit
        // 2026-08-20, M9) -- same rule as the cover image.
        val contentType = FileTypeSniffer.detectImageMime(image.data)
            ?: throw NotFoundException("No story image $imageId for campaign $campaignId")
        return contentType to image.data
    }

    /** Rejects empty files, oversized files, non-image MIME types, and mislabelled bytes. */
    private fun validateStoryImage(file: MultipartFile) {
        if (file.isEmpty) {
            throw UnprocessableEntityException("Story image file is empty")
        }
        if (file.size > MAX_STORY_IMAGE_SIZE) {
            throw UnprocessableEntityException("Story image exceeds the maximum allowed size of 5 MB")
        }
        val mime = file.contentType ?: ""
        if (mime !in STORY_IMAGE_ALLOWED_MIME) {
            throw UnprocessableEntityException(
                "Unsupported story image type '$mime'; allowed types: ${STORY_IMAGE_ALLOWED_MIME.joinToString(", ")}"
            )
        }
    }

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

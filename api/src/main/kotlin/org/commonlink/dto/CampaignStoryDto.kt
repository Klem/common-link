package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.commonlink.entity.CampaignStory
import java.time.Instant

/**
 * A campaign's impact story, association-authored (D5).
 *
 * @property storyText Sanitized rich-text HTML -- safe to render with `dangerouslySetInnerHTML`.
 * @property storySummary Plain text, no HTML -- the only text the impact gallery and the share
 *   card ever compose into the collective wording (D6). [storyText] is never interpolated there.
 */
data class CampaignStoryDto(
    val storyText: String,
    val storySummary: String,
    /** Null = draft, not visible to donors. */
    val publishedAt: Instant?,
)

fun CampaignStory.toDto() = CampaignStoryDto(
    storyText = storyText,
    storySummary = storySummary,
    publishedAt = publishedAt,
)

/** Request body for [org.commonlink.controller.CampaignController]'s story upsert endpoint. */
data class UpsertCampaignStoryRequest(
    @field:NotBlank
    val storyText: String,
    @field:NotBlank
    @field:Size(max = 220)
    val storySummary: String,
    val publish: Boolean = false,
)

/** One uploaded story image, returned to the editor so it can insert `<img src>`. */
data class CampaignStoryImageDto(
    val id: String,
    val url: String,
)

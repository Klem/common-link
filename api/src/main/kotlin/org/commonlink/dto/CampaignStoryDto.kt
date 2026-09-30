package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import org.commonlink.entity.CampaignStory
import java.time.Instant

/** A campaign's impact story, association-authored, free text only (D5). */
data class CampaignStoryDto(
    val storyText: String,
    /** Null = draft, not visible to donors. */
    val publishedAt: Instant?,
)

fun CampaignStory.toDto() = CampaignStoryDto(
    storyText = storyText,
    publishedAt = publishedAt,
)

/** Request body for [org.commonlink.controller.CampaignController]'s story upsert endpoint. */
data class UpsertCampaignStoryRequest(
    @field:NotBlank
    val storyText: String,
    val publish: Boolean = false,
)

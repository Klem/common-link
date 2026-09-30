package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.CampaignStoryService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

/**
 * Public (unauthenticated) serving of campaign story images.
 *
 * Same reasoning as [PublicCampaignCoverController]: an `<img src>` inside the story's rendered
 * HTML cannot carry a Bearer token.
 */
@RestController
@RequestMapping("/api/public/campaigns")
@Tag(name = "Public Campaign Story Images", description = "Campaign story image serving (no authentication required)")
class PublicCampaignStoryImageController(
    private val campaignStoryService: CampaignStoryService,
) {

    /**
     * Streams the bytes of one story image.
     *
     * @param campaignId UUID of the campaign.
     * @param imageId UUID of the image.
     * @return 200 with the image bytes and its sniffed Content-Type, 404 if it does not exist.
     */
    @GetMapping("/{campaignId}/story-images/{imageId}")
    @Operation(
        summary = "Get a campaign story image",
        description = "Returns the raw bytes of one image embedded in a campaign story. No authentication required."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Image returned"),
        ApiResponse(responseCode = "404", description = "No such story image for this campaign", content = [Content()])
    )
    fun getStoryImage(
        @PathVariable campaignId: UUID,
        @PathVariable imageId: UUID,
    ): ResponseEntity<ByteArray> {
        val (contentType, data) = campaignStoryService.getStoryImage(campaignId, imageId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            // Immutable: an image is never replaced in place (the editor inserts a new upload for
            // a new image, the old row is simply no longer referenced) — long cache is safe.
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .body(data)
    }
}

package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.CampaignService
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
 * Public (unauthenticated) serving of campaign cover images.
 *
 * Falls under the `/api/public/` prefix, which is `permitAll()` in
 * [org.commonlink.security.SecurityConfig]. Authentication is impossible here by design: an
 * `<img src>` cannot carry a Bearer token, so the association dashboard, the donation widget and
 * the public minisite all read the image through this same URL.
 */
@RestController
@RequestMapping("/api/public/campaigns")
@Tag(name = "Public Campaign Cover", description = "Campaign cover image serving (no authentication required)")
class PublicCampaignCoverController(
    private val campaignService: CampaignService,
) {

    /**
     * Streams the cover image bytes of a campaign.
     *
     * @param id UUID of the campaign.
     * @return 200 with the image bytes and its original Content-Type, 404 if no image is set.
     */
    @GetMapping("/{id}/cover")
    @Operation(
        summary = "Get campaign cover image",
        description = "Returns the raw cover image of a campaign. No authentication required."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Image returned"),
        ApiResponse(responseCode = "404", description = "Campaign has no cover image", content = [Content()])
    )
    fun getCoverImage(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val (contentType, data) = campaignService.getCoverImage(id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            // Revalidation courte : l'image change quand l'association la remplace, et l'URL
            // est stable (elle porte l'id de campagne, pas un hash de contenu).
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(data)
    }
}

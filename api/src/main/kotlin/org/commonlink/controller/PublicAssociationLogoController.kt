package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.AssociationLandingService
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
 * Public (unauthenticated) serving of association landing page logos.
 *
 * Falls under the `/api/public/` prefix, which is `permitAll()` in
 * [org.commonlink.security.SecurityConfig]. Authentication is impossible here by design: an
 * `<img src>` cannot carry a Bearer token, so the association dashboard preview and the public
 * landing page both read the logo through this same URL.
 */
@RestController
@RequestMapping("/api/public/associations")
@Tag(name = "Public Association Logo", description = "Association landing logo serving (no authentication required)")
class PublicAssociationLogoController(
    private val landingService: AssociationLandingService,
) {

    /**
     * Streams the landing logo bytes of an association.
     *
     * @param id UUID of the association profile.
     * @return 200 with the image bytes and its original Content-Type, 404 if no logo is set.
     */
    @GetMapping("/{id}/logo")
    @Operation(
        summary = "Get association landing logo",
        description = "Returns the raw landing page logo of an association. No authentication required."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Image returned"),
        ApiResponse(responseCode = "404", description = "Association has no logo", content = [Content()])
    )
    fun getLogo(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val (contentType, data) = landingService.getLogo(id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            // Revalidation courte : le logo change quand l'association le remplace, et l'URL
            // est stable (elle porte l'id d'association, pas un hash de contenu).
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(data)
    }
}

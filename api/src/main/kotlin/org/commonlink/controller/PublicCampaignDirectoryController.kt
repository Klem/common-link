package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.PublicCampaignListItemDto
import org.commonlink.service.PublicCampaignDirectoryService
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * Public (unauthenticated) directory of live campaigns, consumed by the landing page `/projets`.
 *
 * Falls under the `/api/public/` prefix, which is `permitAll()` in
 * [org.commonlink.security.SecurityConfig]. Kept separate from
 * [PublicCampaignCoverController], whose contract is image serving.
 */
@RestController
@RequestMapping("/api/public/campaigns")
@Tag(name = "Public Campaign Directory", description = "Live campaign listing (no authentication required)")
class PublicCampaignDirectoryController(
    private val publicCampaignDirectoryService: PublicCampaignDirectoryService,
) {

    /**
     * Lists the campaigns currently open to public donations, newest first.
     *
     * @return 200 with a bounded list; empty when nothing is live.
     */
    @GetMapping
    @Operation(
        summary = "List live campaigns",
        description = "Returns the campaigns currently accepting public donations. No authentication required."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Campaign list returned")
    )
    fun listLiveCampaigns(): ResponseEntity<List<PublicCampaignListItemDto>> =
        ResponseEntity.ok()
            // Endpoint non authentifié rendu à chaque affichage de la page projets : sans cache
            // partagé, chaque visiteur déclenche une requête SQL. Cinq minutes de décalage sur un
            // montant collecté sont sans conséquence ; le rendu de la landing page revalide au même
            // rythme.
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(publicCampaignDirectoryService.listLive())
}

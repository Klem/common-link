package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.PublicWidgetDto
import org.commonlink.service.PublicWidgetService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public (unauthenticated) widget endpoints.
 *
 * Accessible without a JWT - whitelisted in [org.commonlink.security.SecurityConfig]
 * under /api/public/. Exposes only donor-safe data; no internal IDs or sensitive fields.
 */
@RestController
@RequestMapping("/api/public/widget")
@Tag(name = "Public Widget", description = "Public donation widget endpoints (no authentication required)")
class PublicWidgetController(
    private val publicWidgetService: PublicWidgetService,
) {

    /**
     * Resolves a widget token to the campaign details needed by the donation iframe.
     *
     * Returns a safe projection of the campaign - no internal IDs, no association contact details.
     *
     * @param widgetToken Opaque public token identifying the association's widget (e.g. clk_...).
     * @return [PublicWidgetDto] with association name, campaign info, and fundraising progress.
     */
    @GetMapping("/{widgetToken}")
    @Operation(
        summary = "Get widget campaign info",
        description = "Resolves a widget token to the campaign details needed for the donation iframe. No authentication required."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Widget resolved - campaign is live and accepting donations",
            content = [Content(schema = Schema(implementation = PublicWidgetDto::class))]
        ),
        ApiResponse(responseCode = "404", description = "Unknown token or no destination campaign configured", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Destination campaign exists but is not LIVE", content = [Content()])
    )
    fun getWidget(@PathVariable widgetToken: String): ResponseEntity<PublicWidgetDto> =
        ResponseEntity.ok(publicWidgetService.getWidget(widgetToken))
}

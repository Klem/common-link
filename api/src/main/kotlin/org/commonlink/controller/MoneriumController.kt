package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.MoneriumAuthUrlDto
import org.commonlink.dto.MoneriumStatusDto
import org.commonlink.service.MoneriumService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for the Monerium OAuth2 PKCE wallet onboarding flow.
 *
 * Exposes two endpoints:
 * - [getAuthUrl] — generates and returns the Monerium authorization URL (association-only).
 * - [getStatus] — returns whether the association already has a connected Monerium wallet.
 *
 * The OAuth2 callback is handled by [MoneriumWebhookController] at `/api/public/webhooks/monerium`.
 */
@RestController
@RequestMapping("/api/monerium")
@Tag(name = "Monerium", description = "Monerium OAuth2 PKCE wallet onboarding")
class MoneriumController(
    private val moneriumService: MoneriumService,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the Monerium OAuth2 authorization URL for the authenticated association.
     *
     * The frontend opens this URL in a popup window. The PKCE code_verifier is generated
     * and stored server-side; the code_challenge is sent to Monerium.
     *
     * @param principal Authenticated user — must be an association profile.
     * @return [MoneriumAuthUrlDto] containing the full Monerium authorization URL.
     */
    @GetMapping("/auth-url")
    @Operation(
        summary = "Get Monerium authorization URL",
        description = "Generates a Monerium OAuth2 PKCE authorization URL for wallet onboarding. The frontend must open it in a popup."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Authorization URL generated",
            content = [Content(schema = Schema(implementation = MoneriumAuthUrlDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun getAuthUrl(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<MoneriumAuthUrlDto> {
        val authUrl = moneriumService.buildAuthorizationUrl(UUID.fromString(principal.username))
        return ResponseEntity.ok(MoneriumAuthUrlDto(authUrl = authUrl))
    }

    /**
     * Returns whether the authenticated association has a connected Monerium wallet.
     *
     * @param principal Authenticated user — must be an association profile.
     * @return [MoneriumStatusDto] with `connected = true` if a [org.commonlink.entity.MoneriumConnection] exists.
     */
    @GetMapping("/status")
    @Operation(
        summary = "Get Monerium connection status",
        description = "Returns whether the authenticated association already has a connected Monerium wallet."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Status returned",
            content = [Content(schema = Schema(implementation = MoneriumStatusDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun getStatus(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<MoneriumStatusDto> {
        val status = moneriumService.getConnectionStatus(UUID.fromString(principal.username))
        return ResponseEntity.ok(status)
    }
}

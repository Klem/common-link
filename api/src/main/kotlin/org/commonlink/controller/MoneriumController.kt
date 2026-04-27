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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

/**
 * REST controller for the Monerium OAuth2 PKCE wallet onboarding flow.
 *
 * Exposes three endpoints:
 * - [getAuthUrl] — generates and returns the Monerium authorization URL (association-only).
 * - [handleCallback] — public OAuth2 callback called by Monerium after user authorization.
 * - [getStatus] — returns whether the association already has a connected Monerium wallet.
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
     * OAuth2 callback endpoint called by Monerium after the user completes authorization.
     *
     * Exchanges the authorization code for tokens using the stored code_verifier (PKCE),
     * persists the [org.commonlink.entity.MoneriumConnection], then redirects the popup
     * browser window to the frontend success or error page.
     *
     * This endpoint is public and must be whitelisted in [org.commonlink.security.SecurityConfig].
     *
     * @param code Authorization code returned by Monerium.
     * @param state State UUID echoed by Monerium; used to retrieve the PKCE code_verifier.
     */
    @GetMapping("/callback")
    @Operation(
        summary = "Monerium OAuth2 callback",
        description = "Public callback called by Monerium. Exchanges the authorization code and redirects the popup to the frontend success or error page."
    )
    @ApiResponses(
        ApiResponse(responseCode = "302", description = "Redirects popup to frontend success or error page", content = [Content()])
    )
    fun handleCallback(
        @RequestParam code: String,
        @RequestParam state: String,
    ): ResponseEntity<Void> {
        val redirectUrl = try {
            moneriumService.handleCallback(code, state)
            logger.info("Monerium callback succeeded for state {}", state)
            "$frontendUrl/en/dashboard/monerium/success"
        } catch (e: Exception) {
            logger.warn("Monerium callback failed for state {}: {}", state, e.message)
            "$frontendUrl/en/dashboard/monerium/error"
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectUrl))
            .build()
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
        val connected = moneriumService.getConnectionStatus(UUID.fromString(principal.username))
        return ResponseEntity.ok(MoneriumStatusDto(connected = connected))
    }
}

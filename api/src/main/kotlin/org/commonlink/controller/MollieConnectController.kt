package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.MollieKycStatusDto
import org.commonlink.service.MollieConnectService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for the Mollie Connect OAuth2 KYC onboarding flow.
 *
 * Exposes two endpoints:
 * - [getAuthUrl] — generates and returns the Mollie Client Link authorization URL (association-only).
 * - [getStatus] — returns the association's Mollie KYC connection status.
 *
 * The OAuth2 callback is handled by [MollieConnectWebhookController] at
 * `/api/public/webhooks/mollie-connect`.
 */
@RestController
@RequestMapping("/api/mollie/connect")
@Tag(name = "Mollie Connect", description = "KYC onboarding OAuth flow for associations")
class MollieConnectController(
    private val mollieConnectService: MollieConnectService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the Mollie Connect authorization URL for the authenticated association.
     *
     * The frontend opens this URL in a popup window. A CSRF state UUID is generated and
     * stored server-side. The URL points to Mollie's Client Link flow which combines KYC
     * submission and OAuth authorization in a single step.
     *
     * @param principal Authenticated user — must be an association profile.
     * @return Map containing `authUrl` — the full Mollie Connect authorization URL.
     */
    @GetMapping("/auth-url")
    @Operation(
        summary = "Get Mollie Connect authorization URL",
        description = "Generates a Mollie Client Link URL for KYC onboarding. The frontend must open it in a popup."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Authorization URL generated",
            content = [Content(schema = Schema(implementation = Map::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "No signed fiscal mandate yet — required before connecting a bank account", content = [Content()])
    )
    fun getAuthUrl(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<Map<String, String>> {
        val authUrl = mollieConnectService.buildAuthorizationUrl(UUID.fromString(principal.username))
        return ResponseEntity.ok(mapOf("authUrl" to authUrl))
    }

    /**
     * Returns the Mollie KYC connection status for the authenticated association.
     *
     * If a non-COMPLETED connection is stale (last sync > 5 minutes), the backend
     * re-fetches `GET /v2/onboarding/me` from Mollie before responding — this is how
     * `in-review` → `completed` transitions surface without webhooks (Mollie has none).
     *
     * @param principal Authenticated user — must be an association profile.
     * @return [MollieKycStatusDto] with connection and onboarding details.
     */
    @GetMapping("/status")
    @Operation(
        summary = "Get Mollie Connect KYC status",
        description = "Returns the association's Mollie KYC connection status. Triggers a throttled onboarding sync if the status may be stale."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Status returned",
            content = [Content(schema = Schema(implementation = MollieKycStatusDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun getStatus(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<MollieKycStatusDto> {
        val status = mollieConnectService.getConnectionStatus(UUID.fromString(principal.username))
        return ResponseEntity.ok(status)
    }
}

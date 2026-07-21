package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.MoneriumService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * Public webhook controller for the Monerium OAuth2 callback.
 *
 * Monerium redirects the browser popup to this endpoint after the user completes or cancels
 * authorization. Always redirects to a frontend page — never returns a body.
 *
 * Falls under the `/api/public/` prefix, which is `permitAll()` in [org.commonlink.security.SecurityConfig].
 */
@RestController
@RequestMapping("/api/public/webhooks/monerium")
@Tag(name = "Monerium", description = "Monerium OAuth2 PKCE wallet onboarding")
class MoneriumWebhookController(
    private val moneriumService: MoneriumService,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * OAuth2 callback endpoint called by Monerium after the user completes or cancels authorization.
     *
     * Handles three cases:
     * - `error` present (e.g. `access_denied`): user denied authorization — redirects to error page.
     * - `code` absent without `error`: malformed callback — redirects to error page.
     * - `code` present: exchanges the authorization code via PKCE, persists the connection,
     *   and redirects the popup to the frontend success page.
     *
     * @param code Authorization code returned by Monerium (absent when user denies).
     * @param state State UUID echoed by Monerium; used to retrieve the PKCE code_verifier.
     * @param error OAuth2 error code (e.g. `access_denied`) when the user cancels or denies.
     */
    @GetMapping
    @Operation(
        summary = "Monerium OAuth2 callback",
        description = "Public callback called by Monerium. Handles user denial (error param) and successful authorization (code param). Always redirects the popup to the frontend success or error page."
    )
    @ApiResponses(
        ApiResponse(responseCode = "302", description = "Redirects popup to frontend success or error page", content = [Content()])
    )
    fun handleCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam state: String,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        val redirectUrl = when {
            error != null -> {
                logger.warn("Monerium OAuth2 authorization denied: error={}, state={}", error, state)
                "$frontendUrl/en/monerium/error"
            }
            code == null -> {
                logger.warn("Monerium callback received without code or error, state={}", state)
                "$frontendUrl/en/monerium/error"
            }
            else -> try {
                moneriumService.handleCallback(code, state)
                logger.info("Monerium callback succeeded for state {}", state)
                "$frontendUrl/en/monerium/success"
            } catch (e: Exception) {
                logger.warn("Monerium callback failed for state {}: {}", state, e.message)
                "$frontendUrl/en/monerium/error"
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectUrl))
            .build()
    }
}

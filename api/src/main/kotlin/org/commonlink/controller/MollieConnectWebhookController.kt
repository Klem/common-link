package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.MollieConnectService
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
 * Public webhook controller for the Mollie Connect OAuth2 callback.
 *
 * Mollie redirects the browser popup to this endpoint after the user completes or cancels
 * authorization. Always redirects to a frontend page — never returns a body.
 *
 * Falls under the `/api/public/` prefix, which is `permitAll()` in [org.commonlink.security.SecurityConfig].
 */
@RestController
@RequestMapping("/api/public/webhooks/mollie-connect")
@Tag(name = "Mollie Connect", description = "KYC onboarding OAuth flow for associations")
class MollieConnectWebhookController(
    private val mollieConnectService: MollieConnectService,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * OAuth2 callback endpoint called by Mollie after the user completes or cancels authorization.
     *
     * Handles three cases:
     * - `error` present (e.g. `access_denied`): user denied authorization — redirects to error page.
     * - `code` or `state` absent without `error`: malformed callback — redirects to error page.
     * - `code` and `state` present: exchanges the authorization code, persists the connection,
     *   and redirects the popup to the frontend success page.
     *
     * @param code Authorization code returned by Mollie (absent when user denies).
     * @param state CSRF state UUID echoed by Mollie; used to validate the callback origin.
     * @param error OAuth2 error code (e.g. `access_denied`) when the user cancels or denies.
     */
    @GetMapping
    @Operation(
        summary = "Mollie Connect OAuth2 callback (public)",
        description = "Public callback called by Mollie after user authorization. Always redirects the popup to the frontend success or error page."
    )
    @ApiResponses(
        ApiResponse(responseCode = "302", description = "Redirects popup to frontend success or error page", content = [Content()])
    )
    fun handleCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        val redirectBase = "$frontendUrl/en/mollie-connect"
        val redirectUrl = when {
            error != null -> {
                logger.warn("Mollie Connect authorization denied: error={}, state={}", error, state)
                "$redirectBase/error"
            }
            code == null || state == null -> {
                logger.warn("Mollie Connect callback missing required params: code={}, state={}", code != null, state != null)
                "$redirectBase/error"
            }
            else -> try {
                mollieConnectService.handleCallback(code, state)
                logger.info("Mollie Connect callback succeeded for state {}", state)
                "$redirectBase/success"
            } catch (e: Exception) {
                logger.warn("Mollie Connect callback failed for state {}: {}", state, e.message)
                "$redirectBase/error"
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectUrl))
            .build()
    }
}

package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.HelloAssoNotificationUrlResponse
import org.commonlink.dto.HelloAssoVerifyRequest
import org.commonlink.dto.HelloAssoVerifyResponse
import org.commonlink.service.HelloAssoCredentialsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The authenticated association links its HelloAsso account. `ROLE_ASSOCIATION` is enforced by the `/api/association`
 * path matcher in [org.commonlink.security.SecurityConfig].
 */
@RestController
@RequestMapping("/api/association/helloasso")
@Tag(name = "Association · HelloAsso", description = "Linking the association's HelloAsso account (API keys mode)")
class HelloAssoConnectionController(
    private val credentialsService: HelloAssoCredentialsService,
) {

    @PostMapping("/notification-url")
    @Operation(
        summary = "Get the HelloAsso notification URL",
        description = "Returns the URL the association pastes in its HelloAsso back office. Idempotent: creates the " +
            "association's HelloAsso link (not linked yet) with a random token on first call; the token never changes.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Notification URL returned",
            content = [Content(schema = Schema(implementation = HelloAssoNotificationUrlResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(
            responseCode = "503",
            description = "Notification URL not configured on this server (code HELLOASSO_NOTIFICATION_URL_NOT_CONFIGURED)",
            content = [Content()],
        ),
    )
    fun notificationUrl(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<HelloAssoNotificationUrlResponse> =
        ResponseEntity.ok(credentialsService.ensureNotificationUrl(UUID.fromString(principal.username)))

    @PostMapping("/credentials/verify")
    @Operation(
        summary = "Verify the association's HelloAsso API keys",
        description = "Exchanges the keys for tokens at HelloAsso, identifies the single organization they reach, reads " +
            "its eligibility (isCashInCompliant) and checks it is the association verified on CommonLink (RNA, else " +
            "name). Persists nothing; the client secret is never stored, logged nor returned. A matching result is " +
            "kept 10 minutes under verifyId, to be confirmed with the mandate. 5 checks per 10 minutes.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Keys verified; matchesKyb false (and verifyId null) for another organization",
            content = [Content(schema = Schema(implementation = HelloAssoVerifyResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(
            responseCode = "422",
            description = "Missing field, keys refused by HelloAsso (code HELLOASSO_INVALID_KEYS), or keys reaching no " +
                "or several organizations (code HELLOASSO_ORGANIZATION_UNRESOLVED)",
            content = [Content()],
        ),
        ApiResponse(responseCode = "429", description = "Too many checks, or HelloAsso rate limit", content = [Content()]),
        ApiResponse(responseCode = "502", description = "HelloAsso unavailable", content = [Content()]),
    )
    fun verify(
        @Valid @RequestBody request: HelloAssoVerifyRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<HelloAssoVerifyResponse> =
        ResponseEntity.ok(credentialsService.verify(UUID.fromString(principal.username), request))
}

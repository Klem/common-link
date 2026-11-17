package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.HelloAssoConnectRequest
import org.commonlink.dto.HelloAssoDisconnectPreviewResponse
import org.commonlink.dto.HelloAssoNotificationUrlResponse
import org.commonlink.dto.HelloAssoVerifyRequest
import org.commonlink.dto.HelloAssoVerifyResponse
import org.commonlink.service.HelloAssoCredentialsService
import org.commonlink.service.HelloAssoEligibilityService
import org.commonlink.service.HelloAssoLinkService
import org.commonlink.service.HelloAssoOnboardingReminderService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
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
    private val linkService: HelloAssoLinkService,
    private val eligibilityService: HelloAssoEligibilityService,
    private val reminderService: HelloAssoOnboardingReminderService,
) {

    /** Returns the association's HelloAsso notification URL, creating its row on first need. */
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
            responseCode = "409", description = "Association not on HelloAsso (code HELLOASSO_NOT_ASSOCIATION_PROVIDER)",
            content = [Content()],
        ),
        ApiResponse(
            responseCode = "503",
            description = "Notification URL not configured on this server (code HELLOASSO_NOTIFICATION_URL_NOT_CONFIGURED)",
            content = [Content()],
        ),
    )
    fun notificationUrl(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<HelloAssoNotificationUrlResponse> =
        ResponseEntity.ok(credentialsService.ensureNotificationUrl(UUID.fromString(principal.username)))

    /** Verifies the association's HelloAsso API keys; the secret is used once and never stored. */
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
            responseCode = "409", description = "Association not on HelloAsso (code HELLOASSO_NOT_ASSOCIATION_PROVIDER)",
            content = [Content()],
        ),
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

    /** Links the verified HelloAsso account once the mandate is accepted. */
    @PostMapping("/connection")
    @Operation(
        summary = "Link the verified HelloAsso account",
        description = "Confirms the key verification (verifyId) with the association's mandate in its current wording " +
            "version, and persists the link without the client secret (client id, slug, encrypted tokens). The mandate " +
            "(who, when, version) is written to the compliance journal. Also re-links a broken or disconnected account.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Account linked", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(
            responseCode = "409", description = "Association not on HelloAsso (code HELLOASSO_NOT_ASSOCIATION_PROVIDER)",
            content = [Content()],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Mandate not accepted in the current version (code HELLOASSO_MANDATE_REQUIRED), or " +
                "verification unknown / expired / already used (code HELLOASSO_VERIFICATION_EXPIRED)",
            content = [Content()],
        ),
    )
    fun connect(
        @Valid @RequestBody request: HelloAssoConnectRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> {
        linkService.connect(UUID.fromString(principal.username), request)
        return ResponseEntity.noContent().build()
    }

    /** Records that the "no HelloAsso account yet" journey started (idempotent). */
    @PostMapping("/onboarding-started")
    @Operation(
        summary = "Record that the HelloAsso journey started",
        description = "The association started the \"no HelloAsso account yet\" journey (« Commencer »). Idempotent: the " +
            "first time is kept. Drives one reminder email at D+1 and one at D+7, never after the account is linked.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Recorded", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(
            responseCode = "409", description = "Association not on HelloAsso (code HELLOASSO_NOT_ASSOCIATION_PROVIDER)",
            content = [Content()],
        ),
    )
    fun onboardingStarted(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<Void> {
        reminderService.markStarted(UUID.fromString(principal.username))
        return ResponseEntity.noContent().build()
    }

    /** Re-reads the organization's HelloAsso eligibility now (rate-limited). */
    @PostMapping("/eligibility/refresh")
    @Operation(
        summary = "Re-read HelloAsso eligibility now",
        description = "« Vérifier mon dossier » : re-reads the organization's isCashInCompliant at HelloAsso (never a " +
            "checkout-intent). At most once a minute; the result is read through GET /api/association/payment/status.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Eligibility re-read (or HelloAsso unavailable: status unchanged)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(
            responseCode = "409",
            description = "Association not on HelloAsso (code HELLOASSO_NOT_ASSOCIATION_PROVIDER) or not linked " +
                "(code HELLOASSO_NOT_LINKED)",
            content = [Content()],
        ),
        ApiResponse(responseCode = "429", description = "Asked again within a minute", content = [Content()]),
    )
    fun refreshEligibility(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<Void> {
        eligibilityService.requestRefresh(UUID.fromString(principal.username))
        return ResponseEntity.noContent().build()
    }

    /** What a disconnection would leave behind; see [HelloAssoLinkService.disconnectPreview]. */
    @GetMapping("/disconnect-preview")
    @Operation(
        summary = "Preview a HelloAsso disconnection",
        description = "Number and amount (euros) of the association's donations HelloAsso has not paid out yet, every " +
            "campaign, to warn before disconnecting: they can no longer be tracked afterwards and need a manual release. " +
            "Donations already paid out (in transit) are not counted. Never blocks the disconnection.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Preview returned",
            content = [Content(schema = Schema(implementation = HelloAssoDisconnectPreviewResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
    )
    fun disconnectPreview(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<HelloAssoDisconnectPreviewResponse> =
        ResponseEntity.ok(linkService.disconnectPreview(UUID.fromString(principal.username)))

    /** Disconnects the HelloAsso account: tokens erased, row and notification token kept (idempotent). */
    @DeleteMapping("/connection")
    @Operation(
        summary = "Disconnect the HelloAsso account",
        description = "Withdraws CommonLink's HelloAsso access: tokens erased, link disconnected, row and notification " +
            "token kept, compliance journal entry. Idempotent, allowed whatever the association's provider. Campaigns are " +
            "untouched; donations pause through the readiness gate.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Disconnected (or nothing to disconnect)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
    )
    fun disconnect(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<Void> {
        linkService.disconnect(UUID.fromString(principal.username))
        return ResponseEntity.noContent().build()
    }
}

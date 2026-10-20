package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.MollieKycStatusDto
import org.commonlink.dto.MollieOnboardingContextDto
import org.commonlink.dto.MollieSelfCompletionAttestationDto
import org.commonlink.service.MollieConnectService
import org.commonlink.service.MollieOnboardingContextService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for the Mollie Connect OAuth2 KYC onboarding flow.
 *
 * Exposes:
 * - [getAuthUrl] — generates and returns the Mollie Client Link authorization URL (association-only).
 * - [getStatus] — returns the association's Mollie KYC connection status.
 * - [refreshStatus] — forces a rate-limited re-read of that status from Mollie.
 * - [getOnboardingContext] — KYB-derived context for the "Bank account" tab's onboarding guide.
 * - [attestSelfCompletion] — journals the association's self-completion attestation (unlocks nothing).
 *
 * The OAuth2 callback is handled by [MollieConnectWebhookController] at
 * `/api/public/webhooks/mollie-connect`.
 */
@RestController
@RequestMapping("/api/mollie/connect")
@Tag(name = "Mollie Connect", description = "KYC onboarding OAuth flow for associations")
class MollieConnectController(
    private val mollieConnectService: MollieConnectService,
    private val onboardingContextService: MollieOnboardingContextService,
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
     * If a connection that does not collect yet (anything but COMPLETED with payments enabled) is
     * stale (last sync > 5 minutes), the backend re-fetches the configured Mollie onboarding API
     * before responding — one of the ways transitions surface without webhooks (Mollie has none for
     * KYC), alongside the scheduled sweep and [refreshStatus]. The DTO carries
     * `complementRequested` (NEEDS_DATA after an observed IN_REVIEW).
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

    /**
     * Forces a re-read of the association's onboarding status from Mollie and returns it.
     *
     * Bypasses the 5-minute throttle of [getStatus] but calls Mollie at most once every 30 seconds
     * per association; beyond that the stored status is returned (still 200) without calling
     * Mollie. Never calls Mollie for a connection that already collects (COMPLETED with payments
     * enabled), for a BROKEN connection, or when `disable-onboarding-sync` is true. A Mollie
     * failure is not an error: the stored status is returned. The association is always derived
     * from the JWT, so an association can only refresh its own status.
     *
     * @param principal Authenticated user — must be an association profile.
     * @return the same [MollieKycStatusDto] as [getStatus].
     */
    @PostMapping("/status/refresh")
    @Operation(
        summary = "Force a Mollie Connect KYC status refresh",
        description = "Re-reads the onboarding status from Mollie, bypassing the 5-minute read throttle. " +
            "At most one Mollie call per association every 30 seconds; beyond that the stored status is returned. " +
            "Never re-polls a connection that already collects (COMPLETED with canReceivePayments)."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Status returned (refreshed, or stored when rate-limited, skipped or failed)",
            content = [Content(schema = Schema(implementation = MollieKycStatusDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun refreshStatus(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<MollieKycStatusDto> =
        ResponseEntity.ok(mollieConnectService.refreshConnectionStatus(UUID.fromString(principal.username)))

    /**
     * Returns the KYB-derived context the "Bank account" tab needs to guide the association
     * through its own Mollie onboarding: structure variant, KYB verification date, confirmed
     * beneficial owners (name and role only), KYB documents already provided, SIREN, and the
     * self-completion attestation date.
     *
     * Read-only and Mollie-free: never triggers an onboarding sync.
     *
     * @param principal Authenticated user — must be an association profile.
     * @return [MollieOnboardingContextDto].
     */
    @GetMapping("/onboarding-context")
    @Operation(
        summary = "Get Mollie onboarding context",
        description = "Returns CommonLink KYB data used to personalise the Mollie onboarding guide. " +
            "No call to Mollie; never exposes beneficial owners' dates of birth or documents."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Context returned",
            content = [Content(schema = Schema(implementation = MollieOnboardingContextDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun getOnboardingContext(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<MollieOnboardingContextDto> =
        ResponseEntity.ok(onboardingContextService.getOnboardingContext(UUID.fromString(principal.username)))

    /**
     * Records that the association attests having filled its Mollie dossier itself.
     *
     * Idempotent: a second call returns the first attestation without writing a new journal event.
     * Unlocks nothing — the collection lock stays `COMPLETED && canReceivePayments`. Neither the
     * client IP address nor the user-agent is recorded.
     *
     * @param principal Authenticated user — must be an association profile.
     * @return [MollieSelfCompletionAttestationDto] with the (first) attestation timestamp.
     */
    @PostMapping("/self-completion-attestation")
    @Operation(
        summary = "Attest Mollie dossier self-completion",
        description = "Journals MOLLIE_SELF_COMPLETION_ATTESTED (actor, timestamp, text version; no IP, no user-agent). " +
            "Idempotent per association. Unlocks nothing."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Attestation recorded, or the existing one returned",
            content = [Content(schema = Schema(implementation = MollieSelfCompletionAttestationDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun attestSelfCompletion(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<MollieSelfCompletionAttestationDto> =
        ResponseEntity.ok(onboardingContextService.attestSelfCompletion(UUID.fromString(principal.username)))
}

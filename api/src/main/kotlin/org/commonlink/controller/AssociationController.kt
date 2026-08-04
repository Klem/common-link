package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.AssociationProfileDto
import org.commonlink.dto.DashboardStatsDto
import org.commonlink.dto.LandingPreviewTokenDto
import org.commonlink.dto.UpdateAssociationProfileRequest
import org.commonlink.dto.UpdateLandingConfigRequest
import org.commonlink.dto.UpdateWidgetConfigRequest
import org.commonlink.service.AssociationDashboardService
import org.commonlink.service.AssociationLandingService
import org.commonlink.service.AssociationService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/association")
@Tag(name = "Association", description = "Association profile endpoints")
class AssociationController(
    private val associationService: AssociationService,
    private val dashboardService: AssociationDashboardService,
    private val landingService: AssociationLandingService,
) {

    @GetMapping("/me")
    @Operation(
        summary = "Get association profile",
        description = "Returns the association profile for the authenticated user."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Association profile returned",
            content = [Content(schema = Schema(implementation = AssociationProfileDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun getProfile(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<AssociationProfileDto> =
        ResponseEntity.ok(associationService.getProfile(UUID.fromString(principal.username)))

    @PatchMapping("/me")
    @Operation(
        summary = "Update association profile",
        description = "Updates the association profile for the authenticated user. Only provided fields are updated. Name and identifier are not editable."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Association profile updated",
            content = [Content(schema = Schema(implementation = AssociationProfileDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Setting a widget destination campaign requires a completed bank account (Mollie)", content = [Content()])
    )
    fun updateProfile(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: UpdateAssociationProfileRequest
    ): ResponseEntity<AssociationProfileDto> =
        ResponseEntity.ok(associationService.updateProfile(UUID.fromString(principal.username), req))

    @GetMapping("/dashboard")
    @Operation(
        summary = "Get association dashboard statistics",
        description = "Returns aggregate stats for the home screen: total raised on LIVE campaigns, active campaign count, next milestone, 6-month chart, and recent donation activity."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Dashboard statistics returned",
            content = [Content(schema = Schema(implementation = DashboardStatsDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun getDashboard(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<DashboardStatsDto> =
        ResponseEntity.ok(dashboardService.getDashboard(UUID.fromString(principal.username)))

    @PostMapping("/me/widget/token")
    @Operation(
        summary = "Generate or rotate widget token",
        description = "Generates a new cryptographically random widget token (`clk_…`) for the association's donation widget. " +
            "If a token already exists, it is revoked and replaced — existing embeds using the old token will immediately return 404."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "New widget token generated",
            content = [Content(schema = Schema(implementation = WidgetTokenResponse::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Bank account (Mollie) not completed — required before enabling the widget", content = [Content()])
    )
    fun generateWidgetToken(
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<WidgetTokenResponse> {
        val token = associationService.generateWidgetToken(UUID.fromString(principal.username))
        return ResponseEntity.ok(WidgetTokenResponse(token))
    }

    @PatchMapping("/me/widget")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Update widget configuration",
        description = "Sets the allowed origin for post-payment redirects. Null clears the setting."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Widget config updated", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Bank account (Mollie) not completed — required before enabling the widget", content = [Content()])
    )
    fun updateWidgetConfig(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: UpdateWidgetConfigRequest,
    ) {
        associationService.updateWidgetConfig(UUID.fromString(principal.username), req.widgetAllowedOrigin)
    }

    @DeleteMapping("/me/widget/token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete widget token",
        description = "Disables the donation widget by clearing its token. " +
            "Any existing embed using the old token will return 404 until a new token is generated."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Widget token deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun deleteWidgetToken(@AuthenticationPrincipal principal: UserDetails) {
        associationService.deleteWidgetToken(UUID.fromString(principal.username))
    }

    @PatchMapping("/me/landing")
    @Operation(
        summary = "Update landing page configuration",
        description = "Updates the landing page theme and section visibility. Only provided fields are updated."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Landing config updated",
            content = [Content(schema = Schema(implementation = AssociationProfileDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Unknown theme value", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Bank account (Mollie) not completed — required before configuring the landing page", content = [Content()])
    )
    fun updateLandingConfig(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: UpdateLandingConfigRequest,
    ): ResponseEntity<AssociationProfileDto> =
        ResponseEntity.ok(landingService.updateLandingConfig(UUID.fromString(principal.username), req))

    /**
     * Uploads (or replaces) the landing page logo.
     *
     * Accepted types: JPEG, PNG, WebP. Max size: 2 MB — same limits as the frontend upload zone.
     * On success the returned DTO carries the public serving path in `landingLogo`.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param file Multipart image part named `file`.
     * @return 200 with the updated association profile.
     */
    @PostMapping("/me/landing/preview-session")
    @Operation(
        summary = "Issue a landing page preview token",
        description = "Returns a short-lived token (10 min) to pass as the `preview` query parameter of " +
            "`GET /api/public/landing/{widgetToken}`. It lifts the LIVE requirement on the destination " +
            "campaign for this association only, so the page can be reviewed before publication."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Preview token issued",
            content = [Content(schema = Schema(implementation = LandingPreviewTokenDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Bank account (Mollie) not completed", content = [Content()])
    )
    fun issueLandingPreviewToken(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<LandingPreviewTokenDto> =
        ResponseEntity.ok(landingService.issuePreviewToken(UUID.fromString(principal.username)))

    @PutMapping("/me/landing/logo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload landing page logo",
        description = "Uploads or replaces the landing page logo. Accepted types: JPEG, PNG, WebP. Max size: 2 MB."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Logo stored",
            content = [Content(schema = Schema(implementation = AssociationProfileDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Bank account (Mollie) not completed", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Empty file, oversized file or unsupported type", content = [Content()])
    )
    fun uploadLandingLogo(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<AssociationProfileDto> =
        ResponseEntity.ok(landingService.uploadLogo(UUID.fromString(principal.username), file))

    @DeleteMapping("/me/landing/logo")
    @Operation(
        summary = "Delete landing page logo",
        description = "Removes the landing page logo. The landing header then shows the association name alone."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Logo removed",
            content = [Content(schema = Schema(implementation = AssociationProfileDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Bank account (Mollie) not completed", content = [Content()])
    )
    fun deleteLandingLogo(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<AssociationProfileDto> =
        ResponseEntity.ok(landingService.deleteLogo(UUID.fromString(principal.username)))
}

data class WidgetTokenResponse(val token: String)

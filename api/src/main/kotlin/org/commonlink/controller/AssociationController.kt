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
import org.commonlink.dto.UpdateAssociationProfileRequest
import org.commonlink.service.AssociationDashboardService
import org.commonlink.service.AssociationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/association")
@Tag(name = "Association", description = "Association profile endpoints")
class AssociationController(
    private val associationService: AssociationService,
    private val dashboardService: AssociationDashboardService,
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
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
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
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun generateWidgetToken(
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<WidgetTokenResponse> {
        val token = associationService.generateWidgetToken(UUID.fromString(principal.username))
        return ResponseEntity.ok(WidgetTokenResponse(token))
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
}

data class WidgetTokenResponse(val token: String)

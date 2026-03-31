package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.AssociationProfileDto
import org.commonlink.dto.UpdateAssociationProfileRequest
import org.commonlink.service.AssociationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/association")
@Tag(name = "Association", description = "Association profile endpoints")
class AssociationController(
    private val associationService: AssociationService
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
        @RequestBody req: UpdateAssociationProfileRequest
    ): ResponseEntity<AssociationProfileDto> =
        ResponseEntity.ok(associationService.updateProfile(UUID.fromString(principal.username), req))
}

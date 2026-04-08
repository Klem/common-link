package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.DonorProfileDto
import org.commonlink.dto.UpdateDonorProfileRequest
import org.commonlink.service.DonorService
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
@RequestMapping("/api/donor")
@Tag(name = "Donor", description = "Donor profile endpoints")
class DonorController(
    private val donorService: DonorService
) {

    @GetMapping("/me")
    @Operation(
        summary = "Get donor profile",
        description = "Returns the donor profile for the authenticated user."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donor profile returned",
            content = [Content(schema = Schema(implementation = DonorProfileDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun getProfile(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<DonorProfileDto> =
        ResponseEntity.ok(donorService.getProfile(UUID.fromString(principal.username)))

    @PatchMapping("/me")
    @Operation(
        summary = "Update donor profile",
        description = "Updates the donor profile for the authenticated user. Only provided fields are updated."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donor profile updated",
            content = [Content(schema = Schema(implementation = DonorProfileDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun updateProfile(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: UpdateDonorProfileRequest
    ): ResponseEntity<DonorProfileDto> =
        ResponseEntity.ok(donorService.updateProfile(UUID.fromString(principal.username), req))
}

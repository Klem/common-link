package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.AssociationProfileUpsertDto
import org.commonlink.dto.SetPasswordRequestDto
import org.commonlink.dto.UserDto
import org.commonlink.dto.toDto
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.UserRepository
import org.commonlink.service.AuthService
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
@RequestMapping("/api/user")
@Tag(name = "User", description = "User account management endpoints")
class UserController(
    private val authService: AuthService,
    private val userRepository: UserRepository
) {

    @GetMapping("/me")
    @Operation(
        summary = "Get current user",
        description = "Returns the authenticated user's account information."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "User data returned",
            content = [Content(schema = Schema(implementation = UserDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()])
    )
    fun getMe(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<UserDto> {
        val user = userRepository.findById(UUID.fromString(principal.username))
            .orElseThrow { UserNotFoundException() }
        return ResponseEntity.ok(user.toDto())
    }

    @PatchMapping("/me/association-profile")
    @Operation(
        summary = "Create or update association profile",
        description = "Creates or updates the association profile for the authenticated ASSOCIATION user. " +
            "Called at the end of the association signup flow (Step 3)."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Profile saved successfully"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT, or user is not an ASSOCIATION", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun upsertAssociationProfile(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: AssociationProfileUpsertDto
    ): ResponseEntity<Void> {
        authService.upsertAssociationProfile(UUID.fromString(principal.username), req)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/me/password")
    @Operation(
        summary = "Set or update password",
        description = "Sets or updates the password for the authenticated user. " +
            "Useful for users who signed up via Google or Magic Link and want to add a password."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Password updated successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT, or passwords do not match", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun setPassword(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: SetPasswordRequestDto
    ): ResponseEntity<Void> {
        authService.setPassword(UUID.fromString(principal.username), req.password, req.confirmPassword)
        return ResponseEntity.noContent().build()
    }
}

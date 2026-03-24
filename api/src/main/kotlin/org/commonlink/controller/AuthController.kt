package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.AuthResponseDto
import org.commonlink.dto.GoogleAuthRequestDto
import org.commonlink.dto.LoginRequestDto
import org.commonlink.dto.MagicLinkRequestDto
import org.commonlink.dto.MagicLinkVerifyDto
import org.commonlink.dto.RefreshTokenRequestDto
import org.commonlink.dto.RegisterRequestDto
import org.commonlink.dto.ResendVerificationRequestDto
import org.commonlink.dto.VerifyEmailRequestDto
import org.commonlink.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication and registration endpoints")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Creates a new account with email/password and sends a verification email. " +
                "Role must be DONOR or ASSOCIATION."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Account created, verification email sent"),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Email already in use", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun register(@Valid @RequestBody req: RegisterRequestDto): ResponseEntity<Void> {
        authService.register(req)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/verify-email")
    @Operation(
        summary = "Verify email address",
        description = "Exchanges a verification token (from the email link) for access and refresh tokens."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Email verified, tokens returned",
            content = [Content(schema = Schema(implementation = AuthResponseDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Token invalid, expired, or already used", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun verifyEmail(@Valid @RequestBody req: VerifyEmailRequestDto): ResponseEntity<AuthResponseDto> =
        ResponseEntity.ok(authService.verifyEmail(req.token))

    @PostMapping("/resend-verification")
    @Operation(
        summary = "Resend verification email",
        description = "Sends a new verification email to the given address. Rate-limited to 3 per 10 minutes."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Verification email sent (or silently ignored if already verified)"),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = [Content()])
    )
    fun resendVerification(@Valid @RequestBody req: ResendVerificationRequestDto): ResponseEntity<Void> {
        authService.resendVerification(req.email)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/signup/google")
    @Operation(
        summary = "Sign up with Google",
        description = "Creates a new account using a Google ID token. Role is required for new accounts."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Account created, tokens returned",
            content = [Content(schema = Schema(implementation = AuthResponseDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Missing or invalid Google token", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Google token verification failed", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Account already exists", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun signUpWithGoogle(@Valid @RequestBody req: GoogleAuthRequestDto): ResponseEntity<AuthResponseDto> {
        requireNotNull(req.role) { "role is required for Google sign-up" }
        return ResponseEntity.ok(authService.signUpWithGoogle(req.idToken, req.role))
    }

    @PostMapping("/login/google")
    @Operation(
        summary = "Login with Google",
        description = "Authenticates an existing user via Google ID token. " +
                "If the Google account matches an existing email account, it merges them."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Authenticated, tokens returned",
            content = [Content(schema = Schema(implementation = AuthResponseDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(responseCode = "401", description = "No account found or invalid token", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun loginWithGoogle(@Valid @RequestBody req: GoogleAuthRequestDto): ResponseEntity<AuthResponseDto> =
        ResponseEntity.ok(authService.loginWithGoogle(req.idToken))

    @PostMapping("/magic-link/request")
    @Operation(
        summary = "Request a magic link",
        description = "Sends a one-time login link to the given email. " +
                "Role is required only for new accounts. Rate-limited to 3 requests per 10 minutes."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Magic link sent (or silently ignored if rate-limited)"),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(
            responseCode = "401",
            description = "Role required but not provided for new account",
            content = [Content()]
        ),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()]),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = [Content()])
    )
    fun requestMagicLink(@Valid @RequestBody req: MagicLinkRequestDto): ResponseEntity<Void> {
        authService.sendMagicLink(req.email, req.role)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/magic-link/verify")
    @Operation(
        summary = "Verify a magic link token",
        description = "Exchanges a magic link token for access and refresh tokens. " +
                "Creates the user account if it doesn't exist yet."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Token valid, tokens returned",
            content = [Content(schema = Schema(implementation = AuthResponseDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Token invalid or already used", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Token expired", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun verifyMagicLink(@Valid @RequestBody req: MagicLinkVerifyDto): ResponseEntity<AuthResponseDto> =
        ResponseEntity.ok(authService.verifyMagicLink(req.token))

    @PostMapping("/login")
    @Operation(
        summary = "Login with email and password",
        description = "Authenticates a user with email and password credentials."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Authenticated, tokens returned",
            content = [Content(schema = Schema(implementation = AuthResponseDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(
            responseCode = "401",
            description = "Invalid credentials or no password set",
            content = [Content()]
        ),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun login(@Valid @RequestBody req: LoginRequestDto): ResponseEntity<AuthResponseDto> =
        ResponseEntity.ok(authService.loginWithEmail(req.email, req.password))

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh access token",
        description = "Exchanges a valid refresh token for a new access token and a new refresh token (rotation)."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "New tokens returned",
            content = [Content(schema = Schema(implementation = AuthResponseDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(
            responseCode = "401",
            description = "Refresh token invalid, expired, or revoked",
            content = [Content()]
        ),
        ApiResponse(responseCode = "422", description = "Validation errors", content = [Content()])
    )
    fun refresh(@Valid @RequestBody req: RefreshTokenRequestDto): ResponseEntity<AuthResponseDto> =
        ResponseEntity.ok(authService.refreshAccessToken(req.refreshToken))

    @PostMapping("/logout")
    @Operation(
        summary = "Logout",
        description = "Revokes all refresh tokens for the authenticated user. Requires a valid JWT."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Logged out successfully"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()])
    )
    fun logout(@AuthenticationPrincipal principal: UserDetails?): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        authService.logout(UUID.fromString(principal.username))
        return ResponseEntity.noContent().build()
    }
}

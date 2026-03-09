package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.commonlink.dto.AuthResponseDto
import org.commonlink.dto.UserDto
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.UserRole
import org.commonlink.exception.AuthException
import org.commonlink.exception.ConflictException
import org.commonlink.exception.InvalidTokenException
import org.commonlink.exception.PasswordNotSetException
import org.commonlink.exception.RateLimitException
import org.commonlink.exception.TokenExpiredException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AuthService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var authService: AuthService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun authResponse() = AuthResponseDto(
        accessToken = "access.token.jwt",
        refreshToken = "raw-refresh-token",
        user = UserDto(
            id = userId,
            email = "test@example.com",
            role = UserRole.DONOR,
            provider = AuthProvider.EMAIL,
            displayName = null,
            avatarUrl = null,
            emailVerified = true,
            createdAt = Instant.parse("2024-01-01T00:00:00Z")
        )
    )

    // -------------------------------------------------------------------------
    // POST /api/auth/register
    // -------------------------------------------------------------------------

    @Test
    fun `register - 200 returns auth tokens and user`() {
        every { authService.register(any()) } returns authResponse()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com","password":"password123","role":"DONOR"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
            .andExpect(jsonPath("$.refreshToken").value("raw-refresh-token"))
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.role").value("DONOR"))
    }

    @Test
    fun `register - 409 when email already used`() {
        every { authService.register(any()) } throws ConflictException("Email already in use")

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com","password":"password123","role":"DONOR"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("Email already in use"))
    }

    @Test
    fun `register - 422 when email is blank`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"","password":"password123","role":"DONOR"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errors").isArray)
    }

    @Test
    fun `register - 422 when password too short`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com","password":"short","role":"DONOR"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errors").isArray)
    }

    @Test
    fun `register - 400 when role is missing (Jackson deserialization failure)`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com","password":"password123"}""")
        )
            .andExpect(status().isBadRequest)
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/signup/google
    // -------------------------------------------------------------------------

    @Test
    fun `signUpWithGoogle - 200 happy path`() {
        every { authService.signUpWithGoogle("valid-token", UserRole.DONOR) } returns authResponse()

        mockMvc.perform(
            post("/api/auth/signup/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-token","role":"DONOR"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
    }

    @Test
    fun `signUpWithGoogle - 400 when role is null`() {
        mockMvc.perform(
            post("/api/auth/signup/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-token"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `signUpWithGoogle - 409 when account already exists`() {
        every { authService.signUpWithGoogle(any(), any()) } throws ConflictException("Account already exists")

        mockMvc.perform(
            post("/api/auth/signup/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-token","role":"DONOR"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("Account already exists"))
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/login/google
    // -------------------------------------------------------------------------

    @Test
    fun `loginWithGoogle - 200 happy path`() {
        every { authService.loginWithGoogle("valid-token") } returns authResponse()

        mockMvc.perform(
            post("/api/auth/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
    }

    @Test
    fun `loginWithGoogle - 401 when no account found`() {
        every { authService.loginWithGoogle(any()) } throws AuthException("No account found. Signup first")

        mockMvc.perform(
            post("/api/auth/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"unknown-token"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("No account found. Signup first"))
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/magic-link/request
    // -------------------------------------------------------------------------

    @Test
    fun `requestMagicLink - 204 happy path`() {
        justRun { authService.sendMagicLink(any(), any()) }

        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com","role":"DONOR"}""")
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `requestMagicLink - 429 with Retry-After header when rate limited`() {
        every { authService.sendMagicLink(any(), any()) } throws RateLimitException()

        mockMvc.perform(
            post("/api/auth/magic-link/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com","role":"DONOR"}""")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "600"))
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/magic-link/verify
    // -------------------------------------------------------------------------

    @Test
    fun `verifyMagicLink - 200 happy path`() {
        every { authService.verifyMagicLink("valid-token") } returns authResponse()

        mockMvc.perform(
            post("/api/auth/magic-link/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"valid-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
    }

    @Test
    fun `verifyMagicLink - 401 with TOKEN_EXPIRED code when token is expired`() {
        every { authService.verifyMagicLink(any()) } throws TokenExpiredException()

        mockMvc.perform(
            post("/api/auth/magic-link/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"expired-token"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
    }

    @Test
    fun `verifyMagicLink - 401 with TOKEN_INVALID code when token is invalid or used`() {
        every { authService.verifyMagicLink(any()) } throws InvalidTokenException()

        mockMvc.perform(
            post("/api/auth/magic-link/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"bad-token"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------------

    @Test
    fun `login - 200 happy path`() {
        every { authService.loginWithEmail("test@example.com", "password123") } returns authResponse()

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com","password":"password123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
    }

    @Test
    fun `login - 401 with PASSWORD_NOT_SET code when user has no password`() {
        every { authService.loginWithEmail(any(), any()) } throws PasswordNotSetException()

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"google@example.com","password":"password123"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"))
    }

    @Test
    fun `login - 401 when credentials are wrong`() {
        every { authService.loginWithEmail(any(), any()) } throws AuthException("Identifiants incorrects")

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com","password":"wrongpass"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Identifiants incorrects"))
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh - 200 happy path`() {
        every { authService.refreshAccessToken("raw-refresh-token") } returns authResponse()

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"raw-refresh-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
            .andExpect(jsonPath("$.refreshToken").value("raw-refresh-token"))
    }

    @Test
    fun `refresh - 401 when token is invalid`() {
        every { authService.refreshAccessToken(any()) } throws AuthException("Refresh token invalide")

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"bad-token"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Refresh token invalide"))
    }

    @Test
    fun `refresh - 401 with TOKEN_EXPIRED code when refresh token is expired`() {
        every { authService.refreshAccessToken(any()) } throws TokenExpiredException()

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"expired-token"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/logout
    // -------------------------------------------------------------------------

    @Test
    fun `logout - 204 when authenticated`() {
        justRun { authService.logout(userId) }

        mockMvc.perform(post("/api/auth/logout").with(user(userId.toString()).roles("DONOR")))
            .andExpect(status().isNoContent)

        verify { authService.logout(userId) }
    }

    @Test
    fun `logout - 401 when not authenticated`() {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized)
    }
}

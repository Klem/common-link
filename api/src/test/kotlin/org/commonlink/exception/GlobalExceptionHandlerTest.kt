package org.commonlink.exception

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.controller.AuthController
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tests GlobalExceptionHandler by triggering exceptions through real controller endpoints.
 * Each test covers a specific exception type → HTTP status + ProblemDetail structure.
 */
@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class GlobalExceptionHandlerTest {

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

    private val loginEndpoint = post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"test@example.com","password":"password123"}""")

    // -------------------------------------------------------------------------
    // AuthException → 401
    // -------------------------------------------------------------------------

    @Test
    fun `AuthException returns 401 ProblemDetail with detail message`() {
        every { authService.loginWithEmail(any(), any()) } throws AuthException("Authentication required")

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Authentication required"))
    }

    // -------------------------------------------------------------------------
    // PasswordNotSetException → 401 + code: PASSWORD_NOT_SET
    // -------------------------------------------------------------------------

    @Test
    fun `PasswordNotSetException returns 401 with code PASSWORD_NOT_SET`() {
        every { authService.loginWithEmail(any(), any()) } throws PasswordNotSetException()

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"))
    }

    // -------------------------------------------------------------------------
    // TokenExpiredException → 401 + code: TOKEN_EXPIRED
    // -------------------------------------------------------------------------

    @Test
    fun `TokenExpiredException returns 401 with code TOKEN_EXPIRED`() {
        every { authService.loginWithEmail(any(), any()) } throws TokenExpiredException()

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
    }

    // -------------------------------------------------------------------------
    // InvalidTokenException → 401 + code: TOKEN_INVALID
    // -------------------------------------------------------------------------

    @Test
    fun `InvalidTokenException returns 401 with code TOKEN_INVALID`() {
        every { authService.loginWithEmail(any(), any()) } throws InvalidTokenException()

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
    }

    // -------------------------------------------------------------------------
    // UserNotFoundException → 404
    // -------------------------------------------------------------------------

    @Test
    fun `UserNotFoundException returns 404 ProblemDetail`() {
        every { authService.loginWithEmail(any(), any()) } throws UserNotFoundException("User not found")

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("User not found"))
    }

    // -------------------------------------------------------------------------
    // ConflictException → 409
    // -------------------------------------------------------------------------

    @Test
    fun `ConflictException returns 409 ProblemDetail`() {
        every { authService.loginWithEmail(any(), any()) } throws ConflictException("Email already in use")

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("Email already in use"))
    }

    // -------------------------------------------------------------------------
    // RateLimitException → 429 + Retry-After: 600
    // -------------------------------------------------------------------------

    @Test
    fun `RateLimitException returns 429 with Retry-After header`() {
        every { authService.loginWithEmail(any(), any()) } throws RateLimitException()

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(header().string("Retry-After", "600"))
    }

    // -------------------------------------------------------------------------
    // MethodArgumentNotValidException → 422 + errors list
    // -------------------------------------------------------------------------

    @Test
    fun `validation failure returns 422 with errors array at top level`() {
        // Empty email triggers @Email + @NotBlank violations
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"not-an-email","password":"short"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.detail").value("Validation failed"))
            .andExpect(jsonPath("$.errors").isArray)
            .andExpect(jsonPath("$.errors[0]").isString)
    }

    // -------------------------------------------------------------------------
    // IllegalArgumentException → 400
    // -------------------------------------------------------------------------

    @Test
    fun `IllegalArgumentException returns 400 ProblemDetail`() {
        every { authService.loginWithEmail(any(), any()) } throws IllegalArgumentException("Bad argument")

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Bad argument"))
    }

    // -------------------------------------------------------------------------
    // Generic Exception → 500 without exposing internal message
    // -------------------------------------------------------------------------

    @Test
    fun `generic Exception returns 500 without exposing internal message`() {
        every { authService.loginWithEmail(any(), any()) } throws RuntimeException("secret internal error")

        mockMvc.perform(loginEndpoint)
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
            // Internal error message must NOT be exposed
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not("secret internal error")))
    }

    // -------------------------------------------------------------------------
    // ProblemDetail RFC 9457 structure
    // -------------------------------------------------------------------------

    @Test
    fun `ProblemDetail response includes required RFC 9457 fields`() {
        every { authService.loginWithEmail(any(), any()) } throws AuthException("Bad credentials")

        // type is omitted when "about:blank" (RFC 9457 default); status and detail are always present
        mockMvc.perform(loginEndpoint)
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.detail").exists())
    }
}

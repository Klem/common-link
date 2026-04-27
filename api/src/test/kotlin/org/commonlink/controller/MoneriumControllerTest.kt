package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.MoneriumService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(MoneriumController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class MoneriumControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var moneriumService: MoneriumService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    // ── GET /api/monerium/auth-url ────────────────────────────────────────────

    @Test
    fun `getAuthUrl - 200 and returns authUrl when authenticated as ASSOCIATION`() {
        every { moneriumService.buildAuthorizationUrl(userId) } returns
            "https://sandbox.monerium.app/auth?client_id=test&state=abc123"

        mockMvc.perform(
            get("/api/monerium/auth-url")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authUrl").value("https://sandbox.monerium.app/auth?client_id=test&state=abc123"))
    }

    @Test
    fun `getAuthUrl - 401 when unauthenticated`() {
        mockMvc.perform(get("/api/monerium/auth-url"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAuthUrl - 403 when authenticated as DONOR`() {
        mockMvc.perform(
            get("/api/monerium/auth-url")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getAuthUrl - 400 when association profile does not exist`() {
        every { moneriumService.buildAuthorizationUrl(userId) } throws
            IllegalArgumentException("Association not found: $userId")

        mockMvc.perform(
            get("/api/monerium/auth-url")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isBadRequest)
    }

    // ── GET /api/monerium/callback ────────────────────────────────────────────

    @Test
    fun `callback - 302 to frontend success page when code exchange succeeds`() {
        every { moneriumService.handleCallback("auth-code-123", "state-uuid-456") } returns mockk(relaxed = true)

        mockMvc.perform(
            get("/api/monerium/callback")
                .param("code", "auth-code-123")
                .param("state", "state-uuid-456")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/dashboard/monerium/success"))
    }

    @Test
    fun `callback - 302 to frontend error page when code exchange fails`() {
        every { moneriumService.handleCallback(any(), any()) } throws
            IllegalArgumentException("Invalid OAuth state")

        mockMvc.perform(
            get("/api/monerium/callback")
                .param("code", "bad-code")
                .param("state", "bad-state")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/dashboard/monerium/error"))
    }

    @Test
    fun `callback - accessible without JWT (public endpoint)`() {
        every { moneriumService.handleCallback(any(), any()) } returns mockk(relaxed = true)

        mockMvc.perform(
            get("/api/monerium/callback")
                .param("code", "code")
                .param("state", "state")
        )
            .andExpect(status().isFound)
    }

    @Test
    fun `callback - 302 to error page when expired state throws IllegalStateException`() {
        every { moneriumService.handleCallback(any(), any()) } throws
            IllegalStateException("OAuth state expired")

        mockMvc.perform(
            get("/api/monerium/callback")
                .param("code", "code")
                .param("state", "expired-state")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/dashboard/monerium/error"))
    }

    // ── GET /api/monerium/status ──────────────────────────────────────────────

    @Test
    fun `getStatus - 200 with connected=true when wallet is linked`() {
        every { moneriumService.getConnectionStatus(userId) } returns true

        mockMvc.perform(
            get("/api/monerium/status")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
    }

    @Test
    fun `getStatus - 200 with connected=false when wallet is not yet linked`() {
        every { moneriumService.getConnectionStatus(userId) } returns false

        mockMvc.perform(
            get("/api/monerium/status")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(false))
    }

    @Test
    fun `getStatus - 401 when unauthenticated`() {
        mockMvc.perform(get("/api/monerium/status"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getStatus - 403 when authenticated as DONOR`() {
        mockMvc.perform(
            get("/api/monerium/status")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isForbidden)
    }
}

package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.commonlink.dto.MollieKycStatusDto
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.MollieConnectService
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

/**
 * MockMvc slice tests for [MollieConnectController] and [MollieConnectWebhookController].
 *
 * Covers: auth (401/403), happy-path 200s, and all callback redirect outcomes (302 success/error).
 * [MollieConnectService] is fully mocked — no HTTP calls, no DB.
 */
@WebMvcTest(controllers = [MollieConnectController::class, MollieConnectWebhookController::class])
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class MollieConnectControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var mollieConnectService: MollieConnectService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    // ── GET /api/mollie/connect/auth-url ──────────────────────────────────────

    @Test
    fun `getAuthUrl - 200 returns authUrl when authenticated as ASSOCIATION`() {
        every { mollieConnectService.buildAuthorizationUrl(userId) } returns
            "https://my.mollie.com/dashboard/client-link/xxx?client_id=app_test&state=abc"

        mockMvc.perform(
            get("/api/mollie/connect/auth-url")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authUrl").value("https://my.mollie.com/dashboard/client-link/xxx?client_id=app_test&state=abc"))
    }

    @Test
    fun `getAuthUrl - 401 when unauthenticated`() {
        mockMvc.perform(get("/api/mollie/connect/auth-url"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAuthUrl - 403 when authenticated as DONOR`() {
        mockMvc.perform(
            get("/api/mollie/connect/auth-url")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isForbidden)
    }

    // ── GET /api/mollie/connect/status ────────────────────────────────────────

    @Test
    fun `getStatus - 200 returns full MollieKycStatusDto when connected`() {
        every { mollieConnectService.getConnectionStatus(userId) } returns MollieKycStatusDto(
            connected = true,
            pending = false,
            broken = false,
            onboardingStatus = "NEEDS_DATA",
            canReceivePayments = false,
            dashboardUrl = "https://my.mollie.com/dashboard/onboarding",
        )

        mockMvc.perform(
            get("/api/mollie/connect/status")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.pending").value(false))
            .andExpect(jsonPath("$.broken").value(false))
            .andExpect(jsonPath("$.onboardingStatus").value("NEEDS_DATA"))
            .andExpect(jsonPath("$.canReceivePayments").value(false))
            .andExpect(jsonPath("$.dashboardUrl").value("https://my.mollie.com/dashboard/onboarding"))
    }

    @Test
    fun `getStatus - 200 with connected=false when no connection exists`() {
        every { mollieConnectService.getConnectionStatus(userId) } returns MollieKycStatusDto(
            connected = false,
            pending = false,
            broken = false,
            onboardingStatus = null,
            canReceivePayments = null,
            dashboardUrl = null,
        )

        mockMvc.perform(
            get("/api/mollie/connect/status")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.pending").value(false))
            .andExpect(jsonPath("$.broken").value(false))
    }

    @Test
    fun `getStatus - 401 when unauthenticated`() {
        mockMvc.perform(get("/api/mollie/connect/status"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getStatus - 403 when authenticated as DONOR`() {
        mockMvc.perform(
            get("/api/mollie/connect/status")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isForbidden)
    }

    // ── GET /api/public/webhooks/mollie-connect ───────────────────────────────

    @Test
    fun `handleCallback - 302 to error when no params provided (no auth required)`() {
        mockMvc.perform(get("/api/public/webhooks/mollie-connect"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/mollie-connect/error"))
    }

    @Test
    fun `handleCallback - 302 to error when Mollie sends error param`() {
        mockMvc.perform(
            get("/api/public/webhooks/mollie-connect")
                .param("error", "access_denied")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/mollie-connect/error"))
    }

    @Test
    fun `handleCallback - 302 to success when service callback succeeds`() {
        every { mollieConnectService.handleCallback("auth_123", "state_456") } just runs

        mockMvc.perform(
            get("/api/public/webhooks/mollie-connect")
                .param("code", "auth_123")
                .param("state", "state_456")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/mollie-connect/success"))
    }

    @Test
    fun `handleCallback - 302 to error when service throws`() {
        every { mollieConnectService.handleCallback(any(), any()) } throws
            IllegalStateException("State expired")

        mockMvc.perform(
            get("/api/public/webhooks/mollie-connect")
                .param("code", "auth_123")
                .param("state", "state_456")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/mollie-connect/error"))
    }
}

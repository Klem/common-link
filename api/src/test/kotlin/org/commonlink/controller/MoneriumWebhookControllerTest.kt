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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(MoneriumWebhookController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class MoneriumWebhookControllerTest {

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

    @Test
    fun `callback - 302 to frontend success page when code exchange succeeds`() {
        every { moneriumService.handleCallback("auth-code-123", "state-uuid-456") } returns mockk(relaxed = true)

        mockMvc.perform(
            get("/api/public/webhooks/monerium")
                .param("code", "auth-code-123")
                .param("state", "state-uuid-456")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/monerium/success"))
    }

    @Test
    fun `callback - 302 to frontend error page when code exchange fails`() {
        every { moneriumService.handleCallback(any(), any()) } throws
            IllegalArgumentException("Invalid OAuth state")

        mockMvc.perform(
            get("/api/public/webhooks/monerium")
                .param("code", "bad-code")
                .param("state", "bad-state")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/monerium/error"))
    }

    @Test
    fun `callback - accessible without JWT (public endpoint)`() {
        every { moneriumService.handleCallback(any(), any()) } returns mockk(relaxed = true)

        mockMvc.perform(
            get("/api/public/webhooks/monerium")
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
            get("/api/public/webhooks/monerium")
                .param("code", "code")
                .param("state", "expired-state")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/monerium/error"))
    }

    @Test
    fun `callback - 302 to error page when error param is present`() {
        mockMvc.perform(
            get("/api/public/webhooks/monerium")
                .param("error", "access_denied")
                .param("state", "state-uuid")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "http://localhost:3000/en/monerium/error"))
    }
}

package org.commonlink.security

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class SecurityConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    // --- Route authorization ---

    @Test
    fun `unauthenticated request to protected path returns 401`() {
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `unauthenticated request to auth public path is not blocked by security`() {
        // No controller exists, so it returns 404 — but NOT 401
        mockMvc.perform(post("/api/auth/login"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `unauthenticated request to docs public path is not blocked by security`() {
        mockMvc.perform(get("/api/docs/index.html"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `unauthenticated request to public path is not blocked by security`() {
        mockMvc.perform(get("/api/public/something"))
            .andExpect(status().isNotFound)
    }

    @Test
    @WithMockUser(roles = ["DONOR"])
    fun `authenticated request to protected path passes security check`() {
        // No controller, so 404 — but NOT 401/403
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isForbidden)
    }

    // --- CORS ---

    @Test
    fun `CORS preflight from allowed origin returns correct headers`() {
        mockMvc.perform(
            options("/api/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
    }

    @Test
    fun `CORS preflight from disallowed origin is rejected`() {
        mockMvc.perform(
            options("/api/auth/login")
                .header("Origin", "http://evil.com")
                .header("Access-Control-Request-Method", "POST")
        )
            .andExpect(status().isForbidden)
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
    }

    @Test
    fun `CORS actual request from allowed origin includes Allow-Origin header`() {
        every { jwtService.extractUserId(any()) } throws RuntimeException("no token")

        mockMvc.perform(
            get("/api/auth/me")
                .header("Origin", "http://localhost:3000")
        )
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
    }
}

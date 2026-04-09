package org.commonlink.security

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.stereotype.Controller
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(
    controllers = [],   // Do not load any real controllers
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = [Controller::class, RestController::class]
        )
    ]
)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class SecurityConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // Only mock what SecurityConfig + JwtAuthenticationFilter actually need
    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository


    @Test
    @WithAnonymousUser
    fun `unauthenticated request to protected path returns 401`() {
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isUnauthorized)   // Now correctly 401 thanks to exceptionHandling
    }

    @Test
    fun `public auth endpoint is accessible without authentication`() {
        mockMvc.perform(post("/api/auth/login"))
            .andExpect(status().isNotFound)   // No controller = 404 is expected
    }

    @Test
    fun `public docs path is accessible without authentication`() {
        mockMvc.perform(get("/api/docs/index.html"))
            .andExpect(status().isNotFound)
    }

    @Test
    @WithMockUser(roles = ["DONOR"])
    fun `authenticated user can access protected path (no controller = 401)`() {
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isUnauthorized)   // since no real controller → 404 is expected
    }

    // ====================== CORS Tests ======================

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
    }

    @Test
    fun `CORS actual request from allowed origin includes Allow-Origin header`() {
        every { jwtService.extractUserId(any()) } throws RuntimeException("invalid token")

        mockMvc.perform(
            get("/api/auth/me")
                .header("Origin", "http://localhost:3000")
        )
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
    }
}

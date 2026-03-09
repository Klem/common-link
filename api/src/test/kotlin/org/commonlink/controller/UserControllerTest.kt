package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.commonlink.exception.AuthException
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(UserController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class UserControllerTest {

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

    // -------------------------------------------------------------------------
    // PATCH /api/user/me/password
    // -------------------------------------------------------------------------

    @Test
    fun `setPassword - 204 when authenticated and passwords match`() {
        justRun { authService.setPassword(userId, "newpass123", "newpass123") }

        mockMvc.perform(
            patch("/api/user/me/password")
                .with(user(userId.toString()).roles("DONOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"newpass123","confirmPassword":"newpass123"}""")
        )
            .andExpect(status().isNoContent)

        verify { authService.setPassword(userId, "newpass123", "newpass123") }
    }

    @Test
    fun `setPassword - 403 when not authenticated`() {
        mockMvc.perform(
            patch("/api/user/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"newpass123","confirmPassword":"newpass123"}""")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `setPassword - 422 when password is too short`() {
        mockMvc.perform(
            patch("/api/user/me/password")
                .with(user(userId.toString()).roles("DONOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"short","confirmPassword":"short"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errors").isArray)
    }

    @Test
    fun `setPassword - 422 when password is blank`() {
        mockMvc.perform(
            patch("/api/user/me/password")
                .with(user(userId.toString()).roles("DONOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"","confirmPassword":""}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errors").isArray)
    }

    @Test
    fun `setPassword - 401 when passwords do not match`() {
        every { authService.setPassword(any(), any(), any()) } throws
            AuthException("Les mots de passe ne correspondent pas")

        mockMvc.perform(
            patch("/api/user/me/password")
                .with(user(userId.toString()).roles("DONOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"newpass123","confirmPassword":"different1"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Les mots de passe ne correspondent pas"))
    }
}

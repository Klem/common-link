package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.DonorProfileDto
import org.commonlink.dto.UpdateDonorProfileRequest
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.DonorService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(DonorController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class DonorControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var donorService: DonorService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val profileId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val sampleProfile = DonorProfileDto(
        id = profileId,
        displayName = "Jean Dupont",
        anonymous = false
    )

    // -------------------------------------------------------------------------
    // GET /api/donor/me
    // -------------------------------------------------------------------------

    @Test
    fun `getProfile - 200 when authenticated`() {
        every { donorService.getProfile(userId) } returns sampleProfile

        mockMvc.perform(
            get("/api/donor/me")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(profileId.toString()))
            .andExpect(jsonPath("$.displayName").value("Jean Dupont"))
            .andExpect(jsonPath("$.anonymous").value(false))
    }

    @Test
    fun `getProfile - 403 when not authenticated`() {
        mockMvc.perform(get("/api/donor/me"))
            .andExpect(status().isForbidden)
    }

    // -------------------------------------------------------------------------
    // PATCH /api/donor/me
    // -------------------------------------------------------------------------

    @Test
    fun `updateProfile - 200 when authenticated and valid payload`() {
        val updated = sampleProfile.copy(displayName = "Jean Modifié", anonymous = true)
        every { donorService.updateProfile(userId, any()) } returns updated

        mockMvc.perform(
            patch("/api/donor/me")
                .with(user(userId.toString()).roles("DONOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"Jean Modifié","anonymous":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Jean Modifié"))
            .andExpect(jsonPath("$.anonymous").value(true))
    }

    @Test
    fun `updateProfile - 403 when not authenticated`() {
        mockMvc.perform(
            patch("/api/donor/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"Jean"}""")
        )
            .andExpect(status().isForbidden)
    }
}

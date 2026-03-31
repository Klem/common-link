package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.AssociationProfileDto
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AssociationService
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

@WebMvcTest(AssociationController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AssociationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var associationService: AssociationService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val profileId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val sampleProfile = AssociationProfileDto(
        id = profileId,
        name = "Médecins Sans Frontières",
        identifier = "775707227",
        city = "Paris",
        postalCode = "75011",
        contactName = "contact@msf.fr",
        description = "Organisation humanitaire",
        verified = false
    )

    // -------------------------------------------------------------------------
    // GET /api/association/me
    // -------------------------------------------------------------------------

    @Test
    fun `getProfile - 200 when authenticated`() {
        every { associationService.getProfile(userId) } returns sampleProfile

        mockMvc.perform(
            get("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(profileId.toString()))
            .andExpect(jsonPath("$.name").value("Médecins Sans Frontières"))
            .andExpect(jsonPath("$.identifier").value("775707227"))
    }

    @Test
    fun `getProfile - 403 when not authenticated`() {
        mockMvc.perform(get("/api/association/me"))
            .andExpect(status().isForbidden)
    }

    // -------------------------------------------------------------------------
    // PATCH /api/association/me
    // -------------------------------------------------------------------------

    @Test
    fun `updateProfile - 200 when authenticated and valid payload`() {
        val updated = sampleProfile.copy(city = "Lyon", postalCode = "69001", description = "Nouvelle description")
        every { associationService.updateProfile(userId, any()) } returns updated

        mockMvc.perform(
            patch("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"city":"Lyon","postalCode":"69001","description":"Nouvelle description"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.city").value("Lyon"))
            .andExpect(jsonPath("$.postalCode").value("69001"))
            .andExpect(jsonPath("$.description").value("Nouvelle description"))
    }

    @Test
    fun `updateProfile - 403 when not authenticated`() {
        mockMvc.perform(
            patch("/api/association/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"city":"Lyon"}""")
        )
            .andExpect(status().isForbidden)
    }
}

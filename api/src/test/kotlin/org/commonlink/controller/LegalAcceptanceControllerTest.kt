package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.LegalAcceptanceStateDto
import org.commonlink.entity.LegalDocumentType
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.LegalAcceptanceService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(LegalAcceptanceController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class LegalAcceptanceControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var legalAcceptanceService: LegalAcceptanceService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `getState - 200 not yet accepted`() {
        every { legalAcceptanceService.associationAcceptanceStateForUser(userId, LegalDocumentType.CGU) } returns
            LegalAcceptanceStateDto(LegalDocumentType.CGU, "2026-08-26", accepted = false)

        mockMvc.perform(get("/api/association/legal-acceptance/CGU").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(false))
            .andExpect(jsonPath("$.currentVersion").value("2026-08-26"))
    }

    @Test
    fun `getState - requires authentication`() {
        mockMvc.perform(get("/api/association/legal-acceptance/CGU"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getState - forbidden for a non-association role`() {
        mockMvc.perform(get("/api/association/legal-acceptance/CGU").with(user(userId.toString()).roles("DONOR")))
            .andExpect(status().isForbidden)
    }
}

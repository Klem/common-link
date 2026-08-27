package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.LegalDocumentDto
import org.commonlink.entity.LegalDocumentType
import org.commonlink.exception.NotFoundException
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(PublicLegalController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class PublicLegalControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var legalAcceptanceService: LegalAcceptanceService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    @Test
    fun `getCurrent - 200 accessible without authentication`() {
        every { legalAcceptanceService.currentDocumentDto(LegalDocumentType.CGU) } returns
            LegalDocumentDto(LegalDocumentType.CGU, "2026-08-26", "texte des CGU", Instant.parse("2026-08-26T00:00:00Z"))

        mockMvc.perform(get("/api/public/legal/CGU"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value("2026-08-26"))
            .andExpect(jsonPath("$.content").value("texte des CGU"))
    }

    @Test
    fun `getCurrent - 404 when no document was ever published`() {
        every { legalAcceptanceService.currentDocumentDto(LegalDocumentType.CGV) } throws NotFoundException("No published CGV document")

        mockMvc.perform(get("/api/public/legal/CGV"))
            .andExpect(status().isNotFound)
    }
}

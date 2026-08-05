package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AssociationLandingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(PublicAssociationLogoController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class PublicAssociationLogoControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var landingService: AssociationLandingService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val assocId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `getLogo - 200 serves the bytes without authentication`() {
        every { landingService.getLogo(assocId) } returns ("image/png" to ByteArray(8) { 3 })

        mockMvc.perform(get("/api/public/associations/$assocId/logo"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", "max-age=300, public"))
    }

    @Test
    fun `getLogo - 404 when the association has no logo`() {
        every { landingService.getLogo(assocId) } throws NotFoundException("No logo for association $assocId")

        mockMvc.perform(get("/api/public/associations/$assocId/logo"))
            .andExpect(status().isNotFound)
    }
}

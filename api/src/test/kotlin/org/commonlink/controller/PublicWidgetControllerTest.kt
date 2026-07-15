package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.PublicWidgetDto
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.PublicWidgetService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(PublicWidgetController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class PublicWidgetControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var publicWidgetService: PublicWidgetService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000042")

    private val sampleDto = PublicWidgetDto(
        associationName = "Médecins Sans Frontières",
        campaignId = campaignId,
        campaignName = "Urgence Gaza",
        campaignEmoji = "🏥",
        campaignDescription = "Aide médicale d'urgence",
        goal = BigDecimal("10000.00"),
        raised = BigDecimal("3500.00"),
        campaignCoverImage = "https://example.com/cover.jpg",
        currency = "EUR",
    )

    @Test
    fun `getWidget - 200 with safe DTO for valid token and LIVE campaign`() {
        every { publicWidgetService.getWidget("clk_valid") } returns sampleDto

        mockMvc.perform(get("/api/public/widget/clk_valid"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.associationName").value("Médecins Sans Frontières"))
            .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
            .andExpect(jsonPath("$.campaignName").value("Urgence Gaza"))
            .andExpect(jsonPath("$.campaignEmoji").value("🏥"))
            .andExpect(jsonPath("$.campaignDescription").value("Aide médicale d'urgence"))
            .andExpect(jsonPath("$.goal").value(10000.00))
            .andExpect(jsonPath("$.raised").value(3500.00))
            .andExpect(jsonPath("$.campaignCoverImage").value("https://example.com/cover.jpg"))
            .andExpect(jsonPath("$.currency").value("EUR"))
            // Assert no internal fields leak
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.associationId").doesNotExist())
            .andExpect(jsonPath("$.contactEmail").doesNotExist())
            .andExpect(jsonPath("$.identifier").doesNotExist())
            .andExpect(jsonPath("$.verificationStatus").doesNotExist())
            .andExpect(jsonPath("$.widgetToken").doesNotExist())
    }

    @Test
    fun `getWidget - 404 for unknown token`() {
        every { publicWidgetService.getWidget("clk_unknown") } throws NotFoundException("Widget not found")

        mockMvc.perform(get("/api/public/widget/clk_unknown"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getWidget - 404 when no destination campaign configured`() {
        every { publicWidgetService.getWidget("clk_nodest") } throws NotFoundException("No destination campaign configured")

        mockMvc.perform(get("/api/public/widget/clk_nodest"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getWidget - 409 when destination campaign is not LIVE`() {
        every { publicWidgetService.getWidget("clk_paused") } throws ConflictException("Campaign is not accepting donations")

        mockMvc.perform(get("/api/public/widget/clk_paused"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `getWidget - accessible without authentication`() {
        every { publicWidgetService.getWidget("clk_valid") } returns sampleDto

        // No .with(user(...)) — endpoint must be public
        mockMvc.perform(get("/api/public/widget/clk_valid"))
            .andExpect(status().isOk)
    }
}

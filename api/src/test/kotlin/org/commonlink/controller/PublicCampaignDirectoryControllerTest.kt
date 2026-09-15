package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.PublicCampaignListItemDto
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.PublicCampaignDirectoryService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(PublicCampaignDirectoryController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class PublicCampaignDirectoryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var publicCampaignDirectoryService: PublicCampaignDirectoryService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `listLiveCampaigns - 200 sans authentification, avec cache partage`() {
        every { publicCampaignDirectoryService.listLive() } returns listOf(
            PublicCampaignListItemDto(
                campaignId = campaignId,
                campaignName = "Hiver Solidaire",
                campaignEmoji = "🍽",
                campaignCategory = "Solidarité",
                coverImage = "/api/public/campaigns/$campaignId/cover",
                campaignUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                goal = BigDecimal("10000"),
                raised = BigDecimal("4200"),
                milestoneCount = 4,
                associationName = "Les Restos du Coeur",
                associationLogo = null,
                donationUrl = "http://localhost:3000/fr/lp/clk_abc",
            )
        )

        mockMvc.perform(get("/api/public/campaigns"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "max-age=300, public"))
            .andExpect(jsonPath("$[0].campaignName").value("Hiver Solidaire"))
            .andExpect(jsonPath("$[0].donationUrl").value("http://localhost:3000/fr/lp/clk_abc"))
            .andExpect(jsonPath("$[0].milestoneCount").value(4))
            .andExpect(jsonPath("$[0].associationLogo").doesNotExist())
    }

    @Test
    fun `listLiveCampaigns - n'expose ni statut, ni jeton widget, ni identifiant d'association`() {
        every { publicCampaignDirectoryService.listLive() } returns listOf(
            PublicCampaignListItemDto(
                campaignId = campaignId,
                campaignName = "Hiver Solidaire",
                campaignEmoji = "🍽",
                campaignCategory = null,
                coverImage = null,
                campaignUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                goal = BigDecimal("10000"),
                raised = BigDecimal.ZERO,
                milestoneCount = 0,
                associationName = "Les Restos du Coeur",
                associationLogo = null,
                donationUrl = "http://localhost:3000/fr/lp/clk_abc",
            )
        )

        mockMvc.perform(get("/api/public/campaigns"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").doesNotExist())
            .andExpect(jsonPath("$[0].createdAt").doesNotExist())
            .andExpect(jsonPath("$[0].widgetToken").doesNotExist())
            .andExpect(jsonPath("$[0].associationId").doesNotExist())
    }

    @Test
    fun `listLiveCampaigns - 200 avec un tableau vide quand rien n'est live`() {
        every { publicCampaignDirectoryService.listLive() } returns emptyList()

        mockMvc.perform(get("/api/public/campaigns"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)
    }
}

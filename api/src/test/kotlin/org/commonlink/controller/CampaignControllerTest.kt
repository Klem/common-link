package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.commonlink.dto.BudgetItemDto
import org.commonlink.dto.BudgetSectionDto
import org.commonlink.dto.CampaignDto
import org.commonlink.dto.CampaignSummaryDto
import org.commonlink.dto.MilestoneDto
import org.commonlink.entity.BudgetSide
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.MilestoneStatus
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.CampaignService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(CampaignController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class CampaignControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var campaignService: CampaignService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val milestoneId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val sectionId = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val itemId = UUID.fromString("00000000-0000-0000-0000-000000000005")

    private val sampleMilestone = MilestoneDto(
        id = milestoneId,
        emoji = "❄️",
        title = "Urgence Chauffage",
        description = "Matériel de chauffage d'urgence",
        targetAmount = BigDecimal("5000"),
        status = MilestoneStatus.LOCKED,
        sortOrder = 0,
        reachedAt = null,
        createdAt = Instant.now()
    )

    private val sampleSection = BudgetSectionDto(
        id = sectionId,
        side = BudgetSide.EXPENSE,
        code = "60",
        name = "Achats",
        sortOrder = 0,
        items = listOf(
            BudgetItemDto(id = itemId, label = "Fournitures", amount = BigDecimal("620"), sortOrder = 0)
        )
    )

    private val sampleCampaign = CampaignDto(
        id = campaignId,
        name = "Hiver Solidaire 2025",
        emoji = "🌍",
        description = "Campagne test",
        goal = BigDecimal("40000"),
        raised = BigDecimal.ZERO,
        status = CampaignStatus.DRAFT,
        startDate = null,
        endDate = null,
        contractAddress = null,
        budgetSections = listOf(sampleSection),
        milestones = listOf(sampleMilestone),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private val sampleSummary = CampaignSummaryDto(
        id = campaignId,
        name = "Hiver Solidaire 2025",
        emoji = "🌍",
        description = "Campagne test",
        goal = BigDecimal("40000"),
        raised = BigDecimal.ZERO,
        status = CampaignStatus.DRAFT,
        startDate = null,
        endDate = null,
        milestoneCount = 1,
        createdAt = Instant.now()
    )

    // ── GET /api/association/campaigns ────────────────────────────────────────

    @Test
    fun `listCampaigns - 200 when authenticated`() {
        every { campaignService.listCampaigns(userId) } returns listOf(sampleSummary)

        mockMvc.perform(
            get("/api/association/campaigns")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(campaignId.toString()))
            .andExpect(jsonPath("$[0].name").value("Hiver Solidaire 2025"))
            .andExpect(jsonPath("$[0].status").value("DRAFT"))
    }

    @Test
    fun `listCampaigns - 401 without JWT`() {
        mockMvc.perform(get("/api/association/campaigns"))
            .andExpect(status().isUnauthorized)
    }

    // ── POST /api/association/campaigns ───────────────────────────────────────

    @Test
    fun `createCampaign - 201 when valid`() {
        every { campaignService.createCampaign(userId, any()) } returns sampleCampaign

        mockMvc.perform(
            post("/api/association/campaigns")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Hiver Solidaire 2025","goal":40000}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(campaignId.toString()))
            .andExpect(jsonPath("$.name").value("Hiver Solidaire 2025"))
    }

    @Test
    fun `createCampaign - 422 when name is blank`() {
        mockMvc.perform(
            post("/api/association/campaigns")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":""}""")
        )
            .andExpect(status().`is`(422))
    }

    @Test
    fun `createCampaign - 401 without JWT`() {
        mockMvc.perform(
            post("/api/association/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Hiver Solidaire 2025"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── GET /api/association/campaigns/{id} ───────────────────────────────────

    @Test
    fun `getCampaign - 200 with full campaign`() {
        every { campaignService.getCampaign(userId, campaignId) } returns sampleCampaign

        mockMvc.perform(
            get("/api/association/campaigns/$campaignId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(campaignId.toString()))
            .andExpect(jsonPath("$.budgetSections[0].code").value("60"))
            .andExpect(jsonPath("$.milestones[0].title").value("Urgence Chauffage"))
    }

    @Test
    fun `getCampaign - 401 without JWT`() {
        mockMvc.perform(get("/api/association/campaigns/$campaignId"))
            .andExpect(status().isUnauthorized)
    }

    // ── PUT /api/association/campaigns/{id} ───────────────────────────────────

    @Test
    fun `updateCampaign - 200 with updated campaign`() {
        val updated = sampleCampaign.copy(name = "Updated Name", status = CampaignStatus.LIVE)
        every { campaignService.updateCampaign(userId, campaignId, any()) } returns updated

        mockMvc.perform(
            put("/api/association/campaigns/$campaignId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Updated Name","status":"LIVE"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.status").value("LIVE"))
    }

    @Test
    fun `updateCampaign - 401 without JWT`() {
        mockMvc.perform(
            put("/api/association/campaigns/$campaignId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"X"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── DELETE /api/association/campaigns/{id} ────────────────────────────────

    @Test
    fun `deleteCampaign - 204 when found`() {
        justRun { campaignService.deleteCampaign(userId, campaignId) }

        mockMvc.perform(
            delete("/api/association/campaigns/$campaignId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteCampaign - 401 without JWT`() {
        mockMvc.perform(delete("/api/association/campaigns/$campaignId"))
            .andExpect(status().isUnauthorized)
    }

    // ── PUT /api/association/campaigns/{id}/budget ────────────────────────────

    @Test
    fun `saveBudget - 200 with updated budget`() {
        every { campaignService.saveBudget(userId, campaignId, any()) } returns sampleCampaign

        mockMvc.perform(
            put("/api/association/campaigns/$campaignId/budget")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sections":[{"side":"EXPENSE","code":"60","name":"Achats","sortOrder":0,"items":[{"label":"Fournitures","amount":620,"sortOrder":0}]}]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.budgetSections[0].code").value("60"))
    }

    @Test
    fun `saveBudget - 401 without JWT`() {
        mockMvc.perform(
            put("/api/association/campaigns/$campaignId/budget")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sections":[]}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── POST /api/association/campaigns/{id}/milestones ───────────────────────

    @Test
    fun `addMilestone - 201 when valid`() {
        every { campaignService.addMilestone(userId, campaignId, any()) } returns sampleMilestone

        mockMvc.perform(
            post("/api/association/campaigns/$campaignId/milestones")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Urgence Chauffage","targetAmount":5000,"sortOrder":0}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(milestoneId.toString()))
            .andExpect(jsonPath("$.title").value("Urgence Chauffage"))
    }

    @Test
    fun `addMilestone - 401 without JWT`() {
        mockMvc.perform(
            post("/api/association/campaigns/$campaignId/milestones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"X"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── PUT /api/association/campaigns/{id}/milestones/{msId} ─────────────────

    @Test
    fun `updateMilestone - 200 with updated milestone`() {
        val updated = sampleMilestone.copy(status = MilestoneStatus.CURRENT)
        every { campaignService.updateMilestone(userId, campaignId, milestoneId, any()) } returns updated

        mockMvc.perform(
            put("/api/association/campaigns/$campaignId/milestones/$milestoneId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"CURRENT"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CURRENT"))
    }

    @Test
    fun `updateMilestone - 401 without JWT`() {
        mockMvc.perform(
            put("/api/association/campaigns/$campaignId/milestones/$milestoneId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"CURRENT"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── DELETE /api/association/campaigns/{id}/milestones/{msId} ─────────────

    @Test
    fun `deleteMilestone - 204 when found`() {
        justRun { campaignService.deleteMilestone(userId, campaignId, milestoneId) }

        mockMvc.perform(
            delete("/api/association/campaigns/$campaignId/milestones/$milestoneId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteMilestone - 404 without JWT`() {
        mockMvc.perform(
            delete("/api/association/campaigns/$campaignId/milestones/$milestoneId")
        )
            .andExpect(status().isUnauthorized)
    }
}

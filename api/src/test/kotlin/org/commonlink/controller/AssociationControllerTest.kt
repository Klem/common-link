package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.commonlink.dto.ActivityItemDto
import org.commonlink.dto.ActivityType
import org.commonlink.dto.AssociationProfileDto
import org.commonlink.entity.VerificationStatus
import org.commonlink.dto.DashboardStatsDto
import org.commonlink.dto.MonthlyPointDto
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AssociationDashboardService
import org.commonlink.service.AssociationService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
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
    private lateinit var dashboardService: AssociationDashboardService

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
        siren = null,
        creationYear = null,
        contactEmail = null,
        phone = null,
        verificationStatus = VerificationStatus.UNVERIFIED,
        verificationRejectionReason = null,
        verificationSubmittedAt = null,
        verifiedAt = null,
        widgetToken = null,
        widgetDestinationCampaignId = null,
        widgetAllowedOrigin = null,
        addressLine1 = null,
        legalObject = null,
        signerName = null,
        signerRole = null,
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
    fun `getProfile - 401 when not authenticated`() {
        mockMvc.perform(get("/api/association/me"))
            .andExpect(status().isUnauthorized)
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
    fun `updateProfile - 401 when not authenticated`() {
        mockMvc.perform(
            patch("/api/association/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"city":"Lyon"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateProfile - 200 with new information fields`() {
        val updated = sampleProfile.copy(siren = "123456789", creationYear = 1901, contactEmail = "contact@msf.fr", phone = "+33 1 23 45 67 89")
        every { associationService.updateProfile(userId, any()) } returns updated

        mockMvc.perform(
            patch("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"siren":"123456789","creationYear":1901,"contactEmail":"contact@msf.fr","phone":"+33 1 23 45 67 89"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.siren").value("123456789"))
            .andExpect(jsonPath("$.creationYear").value(1901))
            .andExpect(jsonPath("$.contactEmail").value("contact@msf.fr"))
            .andExpect(jsonPath("$.phone").value("+33 1 23 45 67 89"))
    }

    @Test
    fun `updateProfile - 422 when SIREN format invalid`() {
        mockMvc.perform(
            patch("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"siren":"INVALID"}""")
        )
            .andExpect(status().`is`(422))
    }

    @Test
    fun `updateProfile - 400 when creation year out of range`() {
        mockMvc.perform(
            patch("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"creationYear":1500}""")
        )
            .andExpect(status().`is`(422))
    }

    @Test
    fun `updateProfile - 400 when contact email invalid`() {
        mockMvc.perform(
            patch("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"contactEmail":"not-an-email"}""")
        )
            .andExpect(status().`is`(422))
    }

    @Test
    fun `updateProfile - 400 when phone format invalid`() {
        mockMvc.perform(
            patch("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone":"abc"}""")
        )
            .andExpect(status().`is`(422))
    }

    @Test
    fun `getProfile - exposes widgetToken and widgetDestinationCampaignId`() {
        val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000099")
        val profileWithWidget = sampleProfile.copy(
            widgetToken = "clk_abc123",
            widgetDestinationCampaignId = campaignId,
        )
        every { associationService.getProfile(userId) } returns profileWithWidget

        mockMvc.perform(
            get("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.widgetToken").value("clk_abc123"))
            .andExpect(jsonPath("$.widgetDestinationCampaignId").value(campaignId.toString()))
    }

    @Test
    fun `updateProfile - accepts widgetDestinationCampaignId`() {
        val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000099")
        val updated = sampleProfile.copy(widgetDestinationCampaignId = campaignId)
        every { associationService.updateProfile(userId, any()) } returns updated

        mockMvc.perform(
            patch("/api/association/me")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"widgetDestinationCampaignId":"$campaignId"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.widgetDestinationCampaignId").value(campaignId.toString()))
    }

    // -------------------------------------------------------------------------
    // POST /api/association/me/widget/token
    // -------------------------------------------------------------------------

    @Test
    fun `generateWidgetToken - 200 returns clk_ token`() {
        every { associationService.generateWidgetToken(userId) } returns "clk_newtoken123"

        mockMvc.perform(
            post("/api/association/me/widget/token")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("clk_newtoken123"))
    }

    @Test
    fun `generateWidgetToken - 401 when not authenticated`() {
        mockMvc.perform(post("/api/association/me/widget/token"))
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // DELETE /api/association/me/widget/token
    // -------------------------------------------------------------------------

    @Test
    fun `deleteWidgetToken - 204 when authenticated`() {
        justRun { associationService.deleteWidgetToken(userId) }

        mockMvc.perform(
            delete("/api/association/me/widget/token")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteWidgetToken - 401 when not authenticated`() {
        mockMvc.perform(delete("/api/association/me/widget/token"))
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // GET /api/association/dashboard
    // -------------------------------------------------------------------------

    private val sampleDashboard = DashboardStatsDto(
        totalRaisedActive = BigDecimal("1500.00"),
        activeCampaignCount = 2,
        nextMilestone = null,
        avgProgress = BigDecimal("0.3750"),
        donations6Months = listOf(
            MonthlyPointDto("2026-01", BigDecimal("500.00")),
            MonthlyPointDto("2026-02", BigDecimal("1000.00")),
        ),
        recentActivity = listOf(
            ActivityItemDto(ActivityType.DONATION, "Alice D.", BigDecimal("50.00"), Instant.now()),
        ),
    )

    @Test
    fun `getDashboard - 200 returns stats shape when authenticated`() {
        every { dashboardService.getDashboard(userId) } returns sampleDashboard

        mockMvc.perform(
            get("/api/association/dashboard")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalRaisedActive").value(1500.00))
            .andExpect(jsonPath("$.activeCampaignCount").value(2))
            .andExpect(jsonPath("$.avgProgress").isNumber)
            .andExpect(jsonPath("$.donations6Months").isArray)
            .andExpect(jsonPath("$.donations6Months[0].month").value("2026-01"))
            .andExpect(jsonPath("$.recentActivity").isArray)
            .andExpect(jsonPath("$.recentActivity[0].type").value("DONATION"))
    }

    @Test
    fun `getDashboard - 401 when not authenticated`() {
        mockMvc.perform(get("/api/association/dashboard"))
            .andExpect(status().isUnauthorized)
    }
}

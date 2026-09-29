package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.AssociationOptionDto
import org.commonlink.dto.DonorAssociationDto
import org.commonlink.dto.DonorDonationDto
import org.commonlink.dto.DonorDonationFiltersDto
import org.commonlink.dto.DonorProfileDto
import org.commonlink.dto.DonorStatsDto
import org.commonlink.dto.UpdateDonorProfileRequest
import org.commonlink.dto.ReceiptDownloadDto
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.DonorAssociationService
import org.commonlink.service.DonorDashboardService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Instant
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
    private lateinit var donorDashboardService: DonorDashboardService

    @MockkBean
    private lateinit var donorAssociationService: DonorAssociationService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val profileId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val donationId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val associationId = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000005")

    private val sampleProfile = DonorProfileDto(
        id = profileId,
        firstName = "Jean",
        lastName = "Dupont",
        displayName = "Jean Dupont",
        anonymous = false,
        notifyMonthlyReport = true,
        notifyNewPayout = true,
        notifyGoalReached = true,
        notifySuggestions = false,
    )

    private val sampleDonation = DonorDonationDto(
        id = donationId,
        donatedAt = Instant.parse("2026-02-01T10:00:00Z"),
        amount = BigDecimal("100.00"),
        campaignId = campaignId,
        campaignName = "Hiver Solidaire",
        campaignEmoji = "🌍",
        associationId = associationId,
        associationName = "Alpha Asso",
        receiptAvailable = true,
        receiptNumber = "2026-0001",
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
            .andExpect(jsonPath("$.firstName").value("Jean"))
            .andExpect(jsonPath("$.lastName").value("Dupont"))
            .andExpect(jsonPath("$.displayName").value("Jean Dupont"))
            .andExpect(jsonPath("$.anonymous").value(false))
            .andExpect(jsonPath("$.notifyMonthlyReport").value(true))
            .andExpect(jsonPath("$.notifyNewPayout").value(true))
            .andExpect(jsonPath("$.notifyGoalReached").value(true))
            .andExpect(jsonPath("$.notifySuggestions").value(false))
    }

    @Test
    fun `getProfile - 401 when not authenticated`() {
        mockMvc.perform(get("/api/donor/me"))
            .andExpect(status().isUnauthorized)
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
    fun `updateProfile - 401 when not authenticated`() {
        mockMvc.perform(
            patch("/api/donor/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"Jean"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateProfile - accepts the notification preferences`() {
        every { donorService.updateProfile(userId, any()) } returns sampleProfile.copy(notifySuggestions = true)

        mockMvc.perform(
            patch("/api/donor/me")
                .with(user(userId.toString()).roles("DONOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"notifySuggestions":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notifySuggestions").value(true))
    }

    // -------------------------------------------------------------------------
    // GET /api/donor/me/donations
    // -------------------------------------------------------------------------

    @Test
    fun `listDonations - 200 returns a paginated envelope`() {
        every { donorDashboardService.listDonations(userId, null, null, 0, 20) } returns
            PageImpl(listOf(sampleDonation), PageRequest.of(0, 20), 1)

        mockMvc.perform(
            get("/api/donor/me/donations")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.content[0].id").value(donationId.toString()))
            .andExpect(jsonPath("$.content[0].amount").value(100.00))
            .andExpect(jsonPath("$.content[0].campaignName").value("Hiver Solidaire"))
            .andExpect(jsonPath("$.content[0].associationName").value("Alpha Asso"))
            .andExpect(jsonPath("$.content[0].receiptAvailable").value(true))
    }

    @Test
    fun `listDonations - forwards paging and both filters`() {
        every { donorDashboardService.listDonations(userId, associationId, 2026, 2, 5) } returns
            PageImpl(emptyList(), PageRequest.of(2, 5), 0)

        mockMvc.perform(
            get("/api/donor/me/donations")
                .param("page", "2")
                .param("size", "5")
                .param("associationId", associationId.toString())
                .param("year", "2026")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isEmpty)
    }

    @Test
    fun `listDonations - 400 when the service rejects the paging values`() {
        every { donorDashboardService.listDonations(userId, null, null, 0, 500) } throws
            IllegalArgumentException("size must be between 1 and 100")

        mockMvc.perform(
            get("/api/donor/me/donations")
                .param("size", "500")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `listDonations - 401 when not authenticated`() {
        mockMvc.perform(get("/api/donor/me/donations"))
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // GET /api/donor/me/donations/filters · GET /api/donor/me/stats
    // -------------------------------------------------------------------------

    @Test
    fun `getDonationFilters - 200 returns associations and years`() {
        every { donorDashboardService.getFilters(userId) } returns DonorDonationFiltersDto(
            associations = listOf(AssociationOptionDto(associationId, "Alpha Asso")),
            years = listOf(2026, 2024),
        )

        mockMvc.perform(
            get("/api/donor/me/donations/filters")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.associations[0].name").value("Alpha Asso"))
            .andExpect(jsonPath("$.years[0]").value(2026))
            .andExpect(jsonPath("$.years[1]").value(2024))
    }

    @Test
    fun `getStats - 200 returns the headline figures`() {
        every { donorDashboardService.getStats(userId) } returns DonorStatsDto(
            totalDonated = BigDecimal("350.00"),
            donationCount = 3,
            associationCount = 2,
            estimatedTaxReduction = BigDecimal("216.00"),
        )

        mockMvc.perform(
            get("/api/donor/me/stats")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalDonated").value(350.00))
            .andExpect(jsonPath("$.donationCount").value(3))
            .andExpect(jsonPath("$.associationCount").value(2))
            .andExpect(jsonPath("$.estimatedTaxReduction").value(216.00))
    }

    // -------------------------------------------------------------------------
    // GET /api/donor/me/donations/{id}/receipt
    // -------------------------------------------------------------------------

    @Test
    fun `downloadReceipt - 200 returns the PDF as an attachment`() {
        every { donorDashboardService.getReceipt(userId, donationId) } returns
            ReceiptDownloadDto(fileName = "recu-2026-0001.pdf", pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46))

        mockMvc.perform(
            get("/api/donor/me/donations/$donationId/receipt")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", """attachment; filename="recu-2026-0001.pdf""""))
    }

    @Test
    fun `downloadReceipt - 404 when no receipt has been generated`() {
        every { donorDashboardService.getReceipt(userId, donationId) } throws
            NotFoundException("No receipt generated for donation $donationId")

        mockMvc.perform(
            get("/api/donor/me/donations/$donationId/receipt")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isNotFound)
    }

    // -------------------------------------------------------------------------
    // GET /api/donor/me/associations
    // -------------------------------------------------------------------------

    @Test
    fun `listAssociations - 200 returns the supported associations`() {
        every { donorAssociationService.listAssociations(userId) } returns listOf(
            DonorAssociationDto(
                associationId = associationId,
                name = "Alpha Asso",
                category = "Education",
                totalDonated = BigDecimal("150.00"),
                publishedPayoutCount = 4,
                campaignCount = 2,
                lastDonationAt = Instant.parse("2026-02-01T10:00:00Z"),
            )
        )

        mockMvc.perform(
            get("/api/donor/me/associations")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].associationId").value(associationId.toString()))
            .andExpect(jsonPath("$[0].name").value("Alpha Asso"))
            .andExpect(jsonPath("$[0].category").value("Education"))
            .andExpect(jsonPath("$[0].totalDonated").value(150.00))
            .andExpect(jsonPath("$[0].publishedPayoutCount").value(4))
            .andExpect(jsonPath("$[0].campaignCount").value(2))
    }

    @Test
    fun `listAssociations - 401 when not authenticated`() {
        mockMvc.perform(get("/api/donor/me/associations"))
            .andExpect(status().isUnauthorized)
    }
}

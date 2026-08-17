package org.commonlink.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.dto.CreateGuestDonationResponse
import org.commonlink.dto.DonationPublicStatus
import org.commonlink.dto.DonationStatusDto
import org.commonlink.dto.PublicLandingDto
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
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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

    private val objectMapper = ObjectMapper().findAndRegisterModules()

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
        widgetAllowedOrigin = "https://www.msf.fr",
        remainingCapacity = BigDecimal("7500.00"),
    )

    private val validRequest = CreateGuestDonationRequest(
        amount = BigDecimal("25.00"),
        donorEmail = "donor@example.com",
        donorFullName = "Jean Dupont",
        donorBirthDate = java.time.LocalDate.of(1985, 6, 15),
        donorBirthCity = "Lyon",
        donorAddressLine1 = "12 rue de la Paix",
        donorAddressLine2 = null,
        donorPostalCode = "75001",
        donorCity = "Paris",
        donorCountry = "FR",
        anonymousDisplay = false,
        consent = true,
        sourceSite = "https://example.org",
    )

    private val sampleResponse = CreateGuestDonationResponse(
        checkoutUrl = "https://www.mollie.com/checkout/pay/tr_test123",
        paymentId = "tr_test123",
    )

    // ── GET /widget/{token} ──────────────────────────────────────────────────

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
            // The form caps the amount input on this value; absent, it would let the donor fill
            // everything in only to be refused on submit.
            .andExpect(jsonPath("$.remainingCapacity").value(7500.00))
            .andExpect(jsonPath("$.campaignCoverImage").value("https://example.com/cover.jpg"))
            .andExpect(jsonPath("$.currency").value("EUR"))
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
        mockMvc.perform(get("/api/public/widget/clk_unknown")).andExpect(status().isNotFound)
    }

    @Test
    fun `getWidget - 404 when no destination campaign configured`() {
        every { publicWidgetService.getWidget("clk_nodest") } throws NotFoundException("No destination campaign configured")
        mockMvc.perform(get("/api/public/widget/clk_nodest")).andExpect(status().isNotFound)
    }

    @Test
    fun `getWidget - 409 when destination campaign is not LIVE`() {
        every { publicWidgetService.getWidget("clk_paused") } throws ConflictException("Campaign is not accepting donations")
        mockMvc.perform(get("/api/public/widget/clk_paused")).andExpect(status().isConflict)
    }

    @Test
    fun `getWidget - accessible without authentication`() {
        every { publicWidgetService.getWidget("clk_valid") } returns sampleDto
        mockMvc.perform(get("/api/public/widget/clk_valid")).andExpect(status().isOk)
    }

    // ── POST /widget/{token}/donations ───────────────────────────────────────

    @Test
    fun `createDonation - 200 with checkoutUrl for valid request`() {
        every { publicWidgetService.createDonation("clk_valid", any()) } returns sampleResponse

        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkoutUrl").value("https://www.mollie.com/checkout/pay/tr_test123"))
            .andExpect(jsonPath("$.paymentId").value("tr_test123"))
    }

    @Test
    fun `createDonation - accessible without authentication`() {
        every { publicWidgetService.createDonation("clk_valid", any()) } returns sampleResponse

        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest))
        ).andExpect(status().isOk)
    }

    @Test
    fun `createDonation - 422 when amount is below minimum`() {
        val req = validRequest.copy(amount = BigDecimal("0.50"))
        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `createDonation - 422 when amount has more than 2 decimal places`() {
        val req = validRequest.copy(amount = BigDecimal("10.005"))
        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `createDonation - 422 when consent is false`() {
        val req = validRequest.copy(consent = false)
        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `createDonation - 422 when email is invalid`() {
        val req = validRequest.copy(donorEmail = "not-an-email")
        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `createDonation - 422 when donorFullName is blank`() {
        val req = validRequest.copy(donorFullName = "   ")
        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `createDonation - 422 when donorAddressLine1 is blank`() {
        val req = validRequest.copy(donorAddressLine1 = "")
        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `createDonation - 422 when donorCountry is not 2 letters`() {
        val req = validRequest.copy(donorCountry = "FRA")
        mockMvc.perform(
            post("/api/public/widget/clk_valid/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `createDonation - 409 when campaign is not LIVE`() {
        every { publicWidgetService.createDonation("clk_paused", any()) } throws ConflictException("Campaign is not accepting donations")

        mockMvc.perform(
            post("/api/public/widget/clk_paused/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `createDonation - 404 for unknown token`() {
        every { publicWidgetService.createDonation("clk_unknown", any()) } throws NotFoundException("Widget not found")

        mockMvc.perform(
            post("/api/public/widget/clk_unknown/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest))
        ).andExpect(status().isNotFound)
    }

    // ── GET /widget/donations/{paymentId}/status ─────────────────────────────

    @Test
    fun `getDonationStatus - 200 CONFIRMED when donation is confirmed`() {
        every { publicWidgetService.getDonationStatus("tr_test123") } returns
            DonationStatusDto(DonationPublicStatus.CONFIRMED)

        mockMvc.perform(get("/api/public/widget/donations/tr_test123/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.donorId").doesNotExist())
            .andExpect(jsonPath("$.campaignId").doesNotExist())
            .andExpect(jsonPath("$.amount").doesNotExist())
    }

    @Test
    fun `getDonationStatus - 200 PENDING when donation is not yet confirmed`() {
        every { publicWidgetService.getDonationStatus("tr_pending") } returns
            DonationStatusDto(DonationPublicStatus.PENDING)

        mockMvc.perform(get("/api/public/widget/donations/tr_pending/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    fun `getDonationStatus - 404 for unknown paymentId`() {
        every { publicWidgetService.getDonationStatus("tr_unknown") } throws NotFoundException("Payment not found")

        mockMvc.perform(get("/api/public/widget/donations/tr_unknown/status"))
            .andExpect(status().isNotFound)
    }

    // -------------------------------------------------------------------------
    // GET /api/public/landing/{widgetToken} — preview query parameter
    // -------------------------------------------------------------------------

    private val sampleLanding = PublicLandingDto(
        associationName = "Asso Test",
        associationRna = "W123456789",
        addressLine1 = null,
        city = null,
        postalCode = null,
        legalObject = null,
        creationYear = null,
        taxReductionRate = 66,
        campaignId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        campaignName = "Campagne Test",
        campaignEmoji = "🎗",
        campaignDescription = null,
        campaignReason = null,
        campaignImpactGoals = null,
        campaignCategory = null,
        goal = BigDecimal("1000.00"),
        raised = BigDecimal("250.00"),
        coverImage = null,
        budget = emptyList(),
        budgetHash = null,
        milestones = emptyList(),
        remainingCapacity = BigDecimal("850.00"),
    )

    @Test
    fun `getLanding - preview parameter is forwarded to the service`() {
        // Stubbed on the exact expected value: a mangled or dropped parameter leaves the call unstubbed.
        every { publicWidgetService.getLanding("clk_abc", "prev_jwt") } returns sampleLanding

        mockMvc.perform(get("/api/public/landing/clk_abc?preview=prev_jwt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.campaignName").value("Campagne Test"))

        verify(exactly = 1) { publicWidgetService.getLanding("clk_abc", "prev_jwt") }
    }

    @Test
    fun `getLanding - absent preview parameter forwards null`() {
        // Stubbed on the exact null argument: any other value would leave the call unstubbed and fail.
        every { publicWidgetService.getLanding("clk_abc", null) } returns sampleLanding

        mockMvc.perform(get("/api/public/landing/clk_abc"))
            .andExpect(status().isOk)

        verify(exactly = 1) { publicWidgetService.getLanding("clk_abc", null) }
    }

    @Test
    fun `getLanding - 409 when the campaign is not LIVE and no valid preview token is supplied`() {
        every { publicWidgetService.getLanding("clk_abc", any()) } throws
            ConflictException("Campaign is not accepting donations")

        mockMvc.perform(get("/api/public/landing/clk_abc?preview=expired"))
            .andExpect(status().isConflict)
    }
}

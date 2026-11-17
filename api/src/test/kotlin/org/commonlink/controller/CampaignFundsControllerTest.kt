package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.CampaignCashOutDto
import org.commonlink.dto.CampaignFundsResponse
import org.commonlink.dto.CampaignManualReleaseDto
import org.commonlink.entity.FundsState
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.NotFoundException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.CampaignFundsService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(CampaignFundsController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class CampaignFundsControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var fundsService: CampaignFundsService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000007")
    private val campaignId = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
    private val url = "/api/association/campaigns/$campaignId/funds"
    private val next = Instant.parse("2026-11-12T09:15:00Z")

    private fun funds(
        provider: PaymentProviderId = PaymentProviderId.HELLOASSO,
        cashOuts: List<CampaignCashOutDto> = emptyList(),
        nextRefresh: Instant? = null,
        raised: String = "0",
    ) = CampaignFundsResponse(
        provider = provider, raised = BigDecimal(raised), atProvider = BigDecimal("40.00"), payoutPreparing = BigDecimal("25.00"),
        inTransit = BigDecimal("30.00"), available = BigDecimal("70.00"), withdrawn = BigDecimal("9.00"),
        paidOut = BigDecimal("100.00"), pendingPayouts = BigDecimal("50.00"), disbursable = BigDecimal.ZERO,
        cashOuts = cashOuts,
        manualReleases = listOf(CampaignManualReleaseDto(Instant.parse("2026-11-11T15:00:00Z"), BigDecimal("12.00"))),
        lastSyncAt = Instant.parse("2026-11-12T09:00:00Z"), syncError = false, syncErrorCode = null, nextRefreshAvailableAt = nextRefresh,
    )

    private fun asAssociation(request: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) =
        request.with(user(userId.toString()).roles("ASSOCIATION"))

    @Test
    fun `GET returns the funds of a HelloAsso campaign, every state`() {
        every { fundsService.getFunds(campaignId, userId) } returns funds(
            raised = "174.00",
            cashOuts = listOf(
                CampaignCashOutDto(60002, LocalDate.parse("2026-11-10"), BigDecimal("1001.00"), BigDecimal("30.00"), FundsState.IN_TRANSIT,
                    LocalDate.parse("2026-11-12"), null, null),
                CampaignCashOutDto(60001, LocalDate.parse("2026-10-09"), null, BigDecimal("60.00"), FundsState.AVAILABLE,
                    LocalDate.parse("2026-10-13"), Instant.parse("2026-10-12T09:00:00Z"), ReceiptConfirmationMode.MANUAL),
            ),
        )

        mockMvc.perform(asAssociation(get(url)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.provider").value("HELLOASSO"))
            .andExpect(jsonPath("$.raised").value(174.00))
            .andExpect(jsonPath("$.atProvider").value(40.00))
            .andExpect(jsonPath("$.payoutPreparing").value(25.00))
            .andExpect(jsonPath("$.inTransit").value(30.00))
            .andExpect(jsonPath("$.available").value(70.00))
            .andExpect(jsonPath("$.disbursable").value(0))
            .andExpect(jsonPath("$.cashOuts[0].idCashOut").value(60002))
            .andExpect(jsonPath("$.cashOuts[0].cashOutDate").value("2026-11-10"))
            .andExpect(jsonPath("$.cashOuts[0].state").value("IN_TRANSIT"))
            .andExpect(jsonPath("$.cashOuts[1].netTotal").doesNotExist())
            .andExpect(jsonPath("$.cashOuts[1].confirmedBy").value("MANUAL"))
            .andExpect(jsonPath("$.manualReleases[0].campaignAmount").value(12.00))
            .andExpect(jsonPath("$.syncError").value(false))
    }

    @Test
    fun `GET returns the funds of a Mollie campaign without payout`() {
        every { fundsService.getFunds(campaignId, userId) } returns funds(provider = PaymentProviderId.MOLLIE)

        mockMvc.perform(asAssociation(get(url)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.provider").value("MOLLIE"))
            .andExpect(jsonPath("$.cashOuts").isEmpty)
    }

    @Test
    fun `GET on an empty campaign is 200 with zeros`() {
        every { fundsService.getFunds(campaignId, userId) } returns funds()

        mockMvc.perform(asAssociation(get(url))).andExpect(status().isOk).andExpect(jsonPath("$.raised").value(0))
    }

    @Test
    fun `two refreshes in a row both answer 200, the second with the next possible refresh`() {
        every { fundsService.refresh(campaignId, userId) } returns funds(nextRefresh = next) andThen funds(nextRefresh = next)

        mockMvc.perform(asAssociation(post("$url/refresh"))).andExpect(status().isOk)
        mockMvc.perform(asAssociation(post("$url/refresh")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextRefreshAvailableAt").value("2026-11-12T09:15:00Z"))
        verify(exactly = 2) { fundsService.refresh(campaignId, userId) }
    }

    @Test
    fun `another association's campaign is not found`() {
        every { fundsService.getFunds(campaignId, userId) } throws NotFoundException("Campaign not found: $campaignId")
        every { fundsService.refresh(campaignId, userId) } throws NotFoundException("Campaign not found: $campaignId")

        mockMvc.perform(asAssociation(get(url))).andExpect(status().isNotFound)
        mockMvc.perform(asAssociation(post("$url/refresh"))).andExpect(status().isNotFound)
    }

    @Test
    fun `403 for other roles, 401 without JWT, service never called`() {
        for (role in listOf("DONOR", "CURATOR")) {
            mockMvc.perform(get(url).with(user(userId.toString()).roles(role))).andExpect(status().isForbidden)
            mockMvc.perform(post("$url/refresh").with(user(userId.toString()).roles(role))).andExpect(status().isForbidden)
        }
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized)
        mockMvc.perform(post("$url/refresh")).andExpect(status().isUnauthorized)
        verify(exactly = 0) { fundsService.getFunds(any(), any()) }
        verify(exactly = 0) { fundsService.refresh(any(), any()) }
    }
}

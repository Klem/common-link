package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.PayoutDto
import org.commonlink.dto.PayoutSummaryDto
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.PayoutService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(PayoutController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class PayoutControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var payoutService: PayoutService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val assocId    = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val payeeId    = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val ibanId     = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val payoutId   = UUID.fromString("00000000-0000-0000-0000-000000000005")

    private val sampleDto = PayoutDto(
        id = payoutId, campaignId = campaignId, payeeId = payeeId, payeeName = "École Kaolack",
        payeeIbanId = ibanId, ibanValue = "FR7630006000011234567890189",
        amount = BigDecimal("500"), kind = PayoutKind.EXPENSE, typeCode = "60-mat",
        label = "Achat matériel pédagogique", status = PayoutStatus.PENDING,
        createdAt = Instant.now(), confirmedAt = null, onchainJobId = null,
    )

    @Test
    fun `POST create returns 201`() {
        every { payoutService.create(any(), any(), any()) } returns sampleDto

        val body = """{"payeeId":"$payeeId","payeeIbanId":"$ibanId","amount":500.00,"kind":"EXPENSE","typeCode":"60-mat","label":"Achat matériel pédagogique"}"""
        mockMvc.perform(
            post("/api/campaigns/$campaignId/payments")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.amount").value(500.0))
    }

    @Test
    fun `POST create returns 422 when label is too short`() {
        val body = """{"payeeId":"$payeeId","payeeIbanId":"$ibanId","amount":500.00,"kind":"EXPENSE","typeCode":"60-mat","label":"ab"}"""
        mockMvc.perform(
            post("/api/campaigns/$campaignId/payments")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `POST create returns 404 when campaign not found`() {
        every { payoutService.create(any(), any(), any()) } throws NotFoundException("Campaign not found")

        val body = """{"payeeId":"$payeeId","payeeIbanId":"$ibanId","amount":500.00,"kind":"EXPENSE","typeCode":"60-mat","label":"Achat matériel pédago"}"""
        mockMvc.perform(
            post("/api/campaigns/$campaignId/payments")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `POST confirm returns 200`() {
        every { payoutService.confirm(campaignId, payoutId, any()) } returns sampleDto.copy(status = PayoutStatus.CONFIRMED)

        mockMvc.perform(
            post("/api/campaigns/$campaignId/payments/$payoutId/confirm")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
    }

    @Test
    fun `POST confirm returns 409 when already confirmed`() {
        every { payoutService.confirm(any(), any(), any()) } throws ConflictException("Already confirmed")

        mockMvc.perform(
            post("/api/campaigns/$campaignId/payments/$payoutId/confirm")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `GET list returns 200 with page`() {
        every { payoutService.list(any(), any(), any()) } returns PageImpl(listOf(sampleDto))

        mockMvc.perform(
            get("/api/campaigns/$campaignId/payments")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(payoutId.toString()))
    }

    @Test
    fun `GET single returns 200`() {
        every { payoutService.get(campaignId, payoutId, any()) } returns sampleDto

        mockMvc.perform(
            get("/api/campaigns/$campaignId/payments/$payoutId")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(payoutId.toString()))
    }

    @Test
    fun `GET single returns 404 when not found`() {
        every { payoutService.get(any(), any(), any()) } throws NotFoundException("not found")

        mockMvc.perform(
            get("/api/campaigns/$campaignId/payments/$payoutId")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET summary returns 200`() {
        val summary = PayoutSummaryDto(
            confirmedAmount = BigDecimal("1000"), confirmedCount = 2L,
            pendingAmount = BigDecimal("200"), txTotal = 5L, txConfirmed = 2L,
            availableBalance = BigDecimal("4000"),
        )
        every { payoutService.getSummary(campaignId, any()) } returns summary

        mockMvc.perform(
            get("/api/campaigns/$campaignId/payments/summary")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availableBalance").value(4000.0))
    }
}

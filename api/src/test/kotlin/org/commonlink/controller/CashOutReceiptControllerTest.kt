package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.CashOutNotCashedOutException
import org.commonlink.exception.HelloAssoNotLinkedException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.CashOutReceiptDonation
import org.commonlink.service.CashOutReceiptResult
import org.commonlink.service.CashOutReceiptService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

@WebMvcTest(CashOutReceiptController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class CashOutReceiptControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var receiptService: CashOutReceiptService
    @MockkBean private lateinit var associationProfileRepository: AssociationProfileRepository
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000007")
    private val associationId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val url = "/api/association/cash-outs/60001/receipt"

    private fun asRole(role: String) = post(url).with(user(userId.toString()).roles(role))

    private fun ownAssociation() {
        val profile = mockk<AssociationProfile> { every { id } returns associationId }
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(profile)
    }

    @Test
    fun `200 returns the payout's available donations, the payout looked up within the caller's association`() {
        ownAssociation()
        val donationId = UUID.randomUUID()
        every { receiptService.confirm(associationId, 60001, userId) } returns CashOutReceiptResult(
            60001,
            listOf(CashOutReceiptDonation(donationId, UUID.randomUUID(), BigDecimal("10.00"), Instant.parse("2026-11-12T09:00:00Z"), ReceiptConfirmationMode.MANUAL)),
            BigDecimal("30.00"),
        )

        mockMvc.perform(asRole("ASSOCIATION"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.idCashOut").value(60001))
            .andExpect(jsonPath("$.netTotal").value(30.00))
            .andExpect(jsonPath("$.donations[0].donationId").value(donationId.toString()))
            .andExpect(jsonPath("$.donations[0].amount").value(10.00))
            .andExpect(jsonPath("$.donations[0].confirmationMode").value("MANUAL"))
    }

    @Test
    fun `409 with code when a donation is not paid out`() {
        ownAssociation()
        every { receiptService.confirm(associationId, 60001, userId) } throws CashOutNotCashedOutException(60001)

        mockMvc.perform(asRole("ASSOCIATION"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CASH_OUT_NOT_CASHED_OUT"))
    }

    @Test
    fun `409 with code without an active HelloAsso link`() {
        ownAssociation()
        every { receiptService.confirm(associationId, 60001, userId) } throws HelloAssoNotLinkedException()

        mockMvc.perform(asRole("ASSOCIATION"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("HELLOASSO_NOT_LINKED"))
    }

    @Test
    fun `404 for an unknown payout or another association's payout`() {
        ownAssociation()
        every { receiptService.confirm(associationId, 60001, userId) } throws NotFoundException("No donation of this association in HelloAsso payout 60001")

        mockMvc.perform(asRole("ASSOCIATION")).andExpect(status().isNotFound)
    }

    @Test
    fun `403 for any other role, service never called`() {
        for (role in listOf("DONOR", "CURATOR", "COMPLIANCE_OFFICER")) {
            mockMvc.perform(asRole(role)).andExpect(status().isForbidden)
        }
        verify(exactly = 0) { receiptService.confirm(any(), any(), any()) }
    }

    @Test
    fun `401 without JWT`() {
        mockMvc.perform(post(url)).andExpect(status().isUnauthorized)
        verify(exactly = 0) { receiptService.confirm(any(), any(), any()) }
    }
}

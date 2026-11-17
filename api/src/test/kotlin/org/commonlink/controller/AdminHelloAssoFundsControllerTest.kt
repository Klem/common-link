package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.HelloAssoFundsReleaseResponse
import org.commonlink.dto.HelloAssoReleasableDonationDto
import org.commonlink.dto.HelloAssoReleasableFundsResponse
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.FundsReleaseNotAllowedException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.HelloAssoFundsReleaseService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(AdminHelloAssoFundsController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AdminHelloAssoFundsControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var releaseService: HelloAssoFundsReleaseService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val curatorId = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
    private val associationId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val donationId = UUID.fromString("00000000-0000-0000-0000-0000000000d1")
    private val base = "/api/admin/associations/$associationId/helloasso-funds"
    private val pdf = MockMultipartFile("file", "line.pdf", "application/pdf", "%PDF-1.4\n".toByteArray())

    private fun releaseAs(role: String?) = multipart("$base/release").file(pdf)
        .param("donationIds", donationId.toString())
        .param("reason", "Link broken")
        .param("statementLine", "VIR HELLOASSO +10,00")
        .let { if (role == null) it else it.with(user(curatorId.toString()).roles(role)) }

    @Test
    fun `GET lists the releasable donations and the link state for a curator`() {
        every { releaseService.listReleasable(associationId) } returns HelloAssoReleasableFundsResponse(
            HelloAssoConnectionState.DISCONNECTED,
            listOf(HelloAssoReleasableDonationDto(donationId, UUID.randomUUID(), "Campagne", BigDecimal("10.00"), FundsState.AT_PROVIDER, "Transfered", null, null)),
        )

        mockMvc.perform(get(base).with(user(curatorId.toString()).roles("CURATOR")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connectionState").value("DISCONNECTED"))
            .andExpect(jsonPath("$.donations[0].donationId").value(donationId.toString()))
            .andExpect(jsonPath("$.donations[0].fundsState").value("AT_PROVIDER"))
    }

    @Test
    fun `200 releases with the curator as actor`() {
        every { releaseService.release(associationId, listOf(donationId), "Link broken", "VIR HELLOASSO +10,00", any(), curatorId) } returns
            HelloAssoFundsReleaseResponse(UUID.randomUUID(), listOf(donationId), BigDecimal("10.00"), Instant.parse("2026-11-12T09:00:00Z"))

        mockMvc.perform(releaseAs("CURATOR"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.donationIds[0]").value(donationId.toString()))
            .andExpect(jsonPath("$.amount").value(10.00))
    }

    @Test
    fun `409 with code when a donation is already available or withdrawn`() {
        every { releaseService.release(any(), any(), any(), any(), any(), any()) } throws FundsReleaseNotAllowedException("already available")

        mockMvc.perform(releaseAs("CURATOR"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("FUNDS_RELEASE_NOT_ALLOWED"))
    }

    @Test
    fun `422 on invalid evidence`() {
        every { releaseService.release(any(), any(), any(), any(), any(), any()) } throws UnprocessableEntityException("reason is required")

        mockMvc.perform(releaseAs("CURATOR")).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `404 for another association's donation`() {
        every { releaseService.release(any(), any(), any(), any(), any(), any()) } throws NotFoundException("Donation not found for this association")

        mockMvc.perform(releaseAs("CURATOR")).andExpect(status().isNotFound)
    }

    @Test
    fun `403 for ASSOCIATION, COMPLIANCE_OFFICER and DONOR, service never called`() {
        for (role in listOf("ASSOCIATION", "COMPLIANCE_OFFICER", "DONOR")) {
            mockMvc.perform(releaseAs(role)).andExpect(status().isForbidden)
            mockMvc.perform(get(base).with(user(curatorId.toString()).roles(role))).andExpect(status().isForbidden)
        }
        verify(exactly = 0) { releaseService.release(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { releaseService.listReleasable(any()) }
    }

    @Test
    fun `401 without JWT`() {
        mockMvc.perform(releaseAs(null)).andExpect(status().isUnauthorized)
        mockMvc.perform(get(base)).andExpect(status().isUnauthorized)
    }
}

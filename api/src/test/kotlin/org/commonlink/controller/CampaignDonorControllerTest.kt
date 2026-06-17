package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.CampaignDonorDto
import org.commonlink.dto.DonationDto
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.DonorAggregateService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(CampaignDonorController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class CampaignDonorControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var donorAggregateService: DonorAggregateService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val assocId    = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val donorId    = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val donationId = UUID.fromString("00000000-0000-0000-0000-000000000004")

    private val sampleDonorDto = CampaignDonorDto(
        donorId       = donorId,
        displayName   = "Marie Dupont",
        totalAmount   = BigDecimal("150.00"),
        txCount       = 2L,
        lastDonationAt = Instant.now(),
    )

    private val anonymousDonorDto = CampaignDonorDto(
        donorId       = UUID.randomUUID(),
        displayName   = "Anonyme",
        totalAmount   = BigDecimal("50.00"),
        txCount       = 1L,
        lastDonationAt = Instant.now(),
    )

    private val sampleDonationDto = DonationDto(
        id          = donationId,
        amount      = BigDecimal("75.00"),
        providerRef = "monerium:abc-123",
        confirmedAt = Instant.now(),
        createdAt   = Instant.now(),
        onChain     = true,
    )

    @Test
    fun `GET donors returns 200 with page`() {
        every { donorAggregateService.listDonors(campaignId, assocId, null, "amount", 0, 20) } returns
            PageImpl(listOf(sampleDonorDto))

        mockMvc.perform(
            get("/api/campaigns/$campaignId/donors")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].donorId").value(donorId.toString()))
            .andExpect(jsonPath("$.content[0].displayName").value("Marie Dupont"))
            .andExpect(jsonPath("$.content[0].totalAmount").value(150.0))
    }

    @Test
    fun `GET donors with search passes search param to service`() {
        every { donorAggregateService.listDonors(campaignId, assocId, "Marie", "amount", 0, 20) } returns
            PageImpl(listOf(sampleDonorDto))

        mockMvc.perform(
            get("/api/campaigns/$campaignId/donors?search=Marie")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].displayName").value("Marie Dupont"))
    }

    @Test
    fun `GET donors — anonymous donor exposes donorId but displayName is Anonyme`() {
        every { donorAggregateService.listDonors(any(), any(), any(), any(), any(), any()) } returns
            PageImpl(listOf(anonymousDonorDto))

        mockMvc.perform(
            get("/api/campaigns/$campaignId/donors")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].displayName").value("Anonyme"))
            .andExpect(jsonPath("$.content[0].donorId").exists())
    }

    @Test
    fun `GET donors returns 404 when campaign not found`() {
        every { donorAggregateService.listDonors(any(), any(), any(), any(), any(), any()) } throws
            NotFoundException("Campaign not found")

        mockMvc.perform(
            get("/api/campaigns/$campaignId/donors")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET donor donations returns 200 with page`() {
        every { donorAggregateService.listDonorDonations(campaignId, donorId, assocId, 0, 20) } returns
            PageImpl(listOf(sampleDonationDto))

        mockMvc.perform(
            get("/api/campaigns/$campaignId/donors/$donorId/donations")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(donationId.toString()))
            .andExpect(jsonPath("$.content[0].amount").value(75.0))
            .andExpect(jsonPath("$.content[0].onChain").value(true))
    }

    @Test
    fun `GET donor donations returns 404 when campaign not found`() {
        every { donorAggregateService.listDonorDonations(any(), any(), any(), any(), any()) } throws
            NotFoundException("Campaign not found")

        mockMvc.perform(
            get("/api/campaigns/$campaignId/donors/$donorId/donations")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET donation returns 200`() {
        every { donorAggregateService.getDonation(campaignId, donationId, assocId) } returns sampleDonationDto

        mockMvc.perform(
            get("/api/campaigns/$campaignId/donations/$donationId")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(donationId.toString()))
            .andExpect(jsonPath("$.providerRef").value("monerium:abc-123"))
    }

    @Test
    fun `GET donation returns 404 when not found`() {
        every { donorAggregateService.getDonation(any(), any(), any()) } throws
            NotFoundException("Donation not found")

        mockMvc.perform(
            get("/api/campaigns/$campaignId/donations/$donationId")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        ).andExpect(status().isNotFound)
    }
}

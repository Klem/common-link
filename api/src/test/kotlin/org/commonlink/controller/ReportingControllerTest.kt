package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.BudgetVarianceDto
import org.commonlink.dto.SectionVarianceDto
import org.commonlink.dto.TotalsVarianceDto
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.ReportingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(ReportingController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
])
class ReportingControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var reportingService: ReportingService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val assocId    = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val sampleVariance = BudgetVarianceDto(
        charges = listOf(
            SectionVarianceDto("60", "Achats", BigDecimal("1000"), BigDecimal("600"), BigDecimal("-400")),
        ),
        produits = listOf(
            SectionVarianceDto("74", "Subventions", BigDecimal("5000"), BigDecimal("3000"), BigDecimal("-2000")),
        ),
        totals = TotalsVarianceDto(
            totalPlannedCharges  = BigDecimal("1000"),
            totalActualCharges   = BigDecimal("600"),
            totalPlannedProduits = BigDecimal("5000"),
            totalActualProduits  = BigDecimal("3000"),
        ),
    )

    @Test
    fun `GET reporting - returns 200 with variance shape`() {
        every { reportingService.getVariance(campaignId, assocId) } returns sampleVariance

        mockMvc.perform(
            get("/api/campaigns/$campaignId/reporting")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.charges[0].sectionCode").value("60"))
            .andExpect(jsonPath("$.charges[0].planned").value(1000))
            .andExpect(jsonPath("$.charges[0].actual").value(600))
            .andExpect(jsonPath("$.charges[0].variance").value(-400))
            .andExpect(jsonPath("$.produits[0].sectionCode").value("74"))
            .andExpect(jsonPath("$.totals.totalPlannedCharges").value(1000))
            .andExpect(jsonPath("$.totals.totalActualProduits").value(3000))
    }

    @Test
    fun `GET reporting - returns 401 when unauthenticated`() {
        mockMvc.perform(get("/api/campaigns/$campaignId/reporting"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET reporting - returns 404 when campaign not found`() {
        every { reportingService.getVariance(campaignId, assocId) } throws
            NotFoundException("Campaign $campaignId not found")

        mockMvc.perform(
            get("/api/campaigns/$campaignId/reporting")
                .with(user(assocId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNotFound)
    }
}

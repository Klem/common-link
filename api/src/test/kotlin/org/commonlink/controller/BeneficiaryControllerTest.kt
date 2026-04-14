package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.commonlink.dto.BeneficiaryDto
import org.commonlink.dto.BeneficiaryIbanDto
import org.commonlink.dto.VopVerifyResponseDto
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.VopResult
import org.commonlink.exception.AppException
import org.commonlink.exception.ConflictException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.BeneficiaryService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(BeneficiaryController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class BeneficiaryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var beneficiaryService: BeneficiaryService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val beneficiaryId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val ibanId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private val sampleIban = BeneficiaryIbanDto(
        id = ibanId,
        iban = "DE89370400440532013000",
        status = IbanVerificationStatus.FORMAT_VALID,
        vopResult = null,
        vopSuggestedName = null,
        verifiedAt = null
    )

    private val sampleBeneficiary = BeneficiaryDto(
        id = beneficiaryId,
        name = "Les Restos du Coeur",
        identifier1 = "775671356",
        identifier2 = null,
        activityCode = null,
        category = null,
        city = "Paris",
        postalCode = "75001",
        active = true,
        ibans = listOf(sampleIban),
        createdAt = Instant.now()
    )

    private val sampleVopResponse = VopVerifyResponseDto(
        ibanId = ibanId,
        iban = "DE89370400440532013000",
        status = IbanVerificationStatus.VERIFIED,
        vopResult = VopResult.MATCH,
        suggestedName = null,
        message = "VOP verification successful: account holder name matches."
    )

    // ── GET /api/association/beneficiaries ────────────────────────────────────

    @Test
    fun `listBeneficiaries - 200 when authenticated`() {
        every { beneficiaryService.listBeneficiaries(userId) } returns listOf(sampleBeneficiary)

        mockMvc.perform(
            get("/api/association/beneficiaries")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(beneficiaryId.toString()))
            .andExpect(jsonPath("$[0].name").value("Les Restos du Coeur"))
            .andExpect(jsonPath("$[0].identifier1").value("775671356"))
            .andExpect(jsonPath("$[0].ibans[0].iban").value("DE89370400440532013000"))
    }

    @Test
    fun `listBeneficiaries - 401 without JWT`() {
        mockMvc.perform(get("/api/association/beneficiaries"))
            .andExpect(status().isUnauthorized)
    }

    // ── POST /api/association/beneficiaries ───────────────────────────────────

    @Test
    fun `createBeneficiary - 201 when valid`() {
        every { beneficiaryService.createBeneficiary(userId, any()) } returns sampleBeneficiary

        mockMvc.perform(
            post("/api/association/beneficiaries")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Les Restos du Coeur","identifier1":"775671356"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(beneficiaryId.toString()))
            .andExpect(jsonPath("$.name").value("Les Restos du Coeur"))
    }

    @Test
    fun `createBeneficiary - 409 on duplicate SIREN`() {
        every { beneficiaryService.createBeneficiary(userId, any()) } throws
            ConflictException("Beneficiary with this identifier already exists for this association")

        mockMvc.perform(
            post("/api/association/beneficiaries")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Duplicate Org","identifier1":"775671356"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `createBeneficiary - 400 when name is blank`() {
        mockMvc.perform(
            post("/api/association/beneficiaries")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"","identifier1":"775671356"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createBeneficiary - 400 when identifier1 is not 9 digits`() {
        mockMvc.perform(
            post("/api/association/beneficiaries")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Valid Name","identifier1":"12345"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createBeneficiary - 401 without JWT`() {
        mockMvc.perform(
            post("/api/association/beneficiaries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Les Restos du Coeur","identifier1":"775671356"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── DELETE /api/association/beneficiaries/{id} ────────────────────────────

    @Test
    fun `deleteBeneficiary - 204 when found`() {
        justRun { beneficiaryService.deleteBeneficiary(userId, beneficiaryId) }

        mockMvc.perform(
            delete("/api/association/beneficiaries/$beneficiaryId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteBeneficiary - 401 without JWT`() {
        mockMvc.perform(delete("/api/association/beneficiaries/$beneficiaryId"))
            .andExpect(status().isUnauthorized)
    }

    // ── POST /api/association/beneficiaries/{id}/ibans ────────────────────────

    @Test
    fun `addIban - 201 when valid`() {
        every { beneficiaryService.addIban(userId, beneficiaryId, any()) } returns sampleBeneficiary

        mockMvc.perform(
            post("/api/association/beneficiaries/$beneficiaryId/ibans")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"iban":"DE89370400440532013000"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ibans[0].iban").value("DE89370400440532013000"))
    }

    @Test
    fun `addIban - 400 when IBAN is blank`() {
        mockMvc.perform(
            post("/api/association/beneficiaries/$beneficiaryId/ibans")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"iban":""}""")
        )
            .andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `addIban - 401 without JWT`() {
        mockMvc.perform(
            post("/api/association/beneficiaries/$beneficiaryId/ibans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"iban":"DE89370400440532013000"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── DELETE /api/association/beneficiaries/{id}/ibans/{ibanId} ─────────────

    @Test
    fun `deleteIban - 204 when found`() {
        justRun { beneficiaryService.deleteIban(userId, beneficiaryId, ibanId) }

        mockMvc.perform(
            delete("/api/association/beneficiaries/$beneficiaryId/ibans/$ibanId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteIban - 401 without JWT`() {
        mockMvc.perform(
            delete("/api/association/beneficiaries/$beneficiaryId/ibans/$ibanId")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── POST /{beneficiaryId}/ibans/{ibanId}/verify-vop ───────────────────────

    @Test
    fun `verifyIbanVop - 200 with VOP result`() {
        every { beneficiaryService.verifyIbanVop(userId, beneficiaryId, ibanId) } returns sampleVopResponse

        mockMvc.perform(
            post("/api/association/beneficiaries/$beneficiaryId/ibans/$ibanId/verify-vop")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ibanId").value(ibanId.toString()))
            .andExpect(jsonPath("$.vopResult").value("MATCH"))
            .andExpect(jsonPath("$.status").value("VERIFIED"))
    }

    @Test
    fun `verifyIbanVop - 401 without JWT`() {
        mockMvc.perform(
            post("/api/association/beneficiaries/$beneficiaryId/ibans/$ibanId/verify-vop")
        )
            .andExpect(status().isUnauthorized)
    }
}

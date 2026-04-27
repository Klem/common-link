package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.commonlink.dto.PayeeDto
import org.commonlink.dto.PayeeIbanDto
import org.commonlink.dto.VopVerifyResponseDto
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.VopResult
import org.commonlink.exception.ConflictException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.PayeeService
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

@WebMvcTest(PayeeController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class PayeeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var payeeService: PayeeService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val payeeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val ibanId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private val sampleIban = PayeeIbanDto(
        id = ibanId,
        iban = "DE89370400440532013000",
        status = IbanVerificationStatus.FORMAT_VALID,
        vopResult = null,
        vopSuggestedName = null,
        verifiedAt = null
    )

    private val samplePayee = PayeeDto(
        id = payeeId,
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

    // ── GET /api/association/payees ────────────────────────────────────────────

    @Test
    fun `listPayees - 200 when authenticated`() {
        every { payeeService.listPayees(userId) } returns listOf(samplePayee)

        mockMvc.perform(
            get("/api/association/payees")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(payeeId.toString()))
            .andExpect(jsonPath("$[0].name").value("Les Restos du Coeur"))
            .andExpect(jsonPath("$[0].identifier1").value("775671356"))
            .andExpect(jsonPath("$[0].ibans[0].iban").value("DE89370400440532013000"))
    }

    @Test
    fun `listPayees - 401 without JWT`() {
        mockMvc.perform(get("/api/association/payees"))
            .andExpect(status().isUnauthorized)
    }

    // ── POST /api/association/payees ───────────────────────────────────────────

    @Test
    fun `createPayee - 201 when valid`() {
        every { payeeService.createPayee(userId, any()) } returns samplePayee

        mockMvc.perform(
            post("/api/association/payees")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Les Restos du Coeur","identifier1":"775671356"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(payeeId.toString()))
            .andExpect(jsonPath("$.name").value("Les Restos du Coeur"))
    }

    @Test
    fun `createPayee - 409 on duplicate SIREN`() {
        every { payeeService.createPayee(userId, any()) } throws
            ConflictException("Payee with this identifier already exists for this association")

        mockMvc.perform(
            post("/api/association/payees")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Duplicate Org","identifier1":"775671356"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `createPayee - 400 when name is blank`() {
        mockMvc.perform(
            post("/api/association/payees")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"","identifier1":"775671356"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createPayee - 400 when identifier1 is not 9 digits`() {
        mockMvc.perform(
            post("/api/association/payees")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Valid Name","identifier1":"12345"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createPayee - 401 without JWT`() {
        mockMvc.perform(
            post("/api/association/payees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Les Restos du Coeur","identifier1":"775671356"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── DELETE /api/association/payees/{id} ────────────────────────────────────

    @Test
    fun `deletePayee - 204 when found`() {
        justRun { payeeService.deletePayee(userId, payeeId) }

        mockMvc.perform(
            delete("/api/association/payees/$payeeId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deletePayee - 401 without JWT`() {
        mockMvc.perform(delete("/api/association/payees/$payeeId"))
            .andExpect(status().isUnauthorized)
    }

    // ── POST /api/association/payees/{id}/ibans ────────────────────────────────

    @Test
    fun `addIban - 201 when valid`() {
        every { payeeService.addIban(userId, payeeId, any()) } returns samplePayee

        mockMvc.perform(
            post("/api/association/payees/$payeeId/ibans")
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
            post("/api/association/payees/$payeeId/ibans")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"iban":""}""")
        )
            .andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `addIban - 401 without JWT`() {
        mockMvc.perform(
            post("/api/association/payees/$payeeId/ibans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"iban":"DE89370400440532013000"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── DELETE /api/association/payees/{id}/ibans/{ibanId} ─────────────────────

    @Test
    fun `deleteIban - 204 when found`() {
        justRun { payeeService.deleteIban(userId, payeeId, ibanId) }

        mockMvc.perform(
            delete("/api/association/payees/$payeeId/ibans/$ibanId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteIban - 401 without JWT`() {
        mockMvc.perform(
            delete("/api/association/payees/$payeeId/ibans/$ibanId")
        )
            .andExpect(status().isUnauthorized)
    }

    // ── POST /{payeeId}/ibans/{ibanId}/verify-vop ──────────────────────────────

    @Test
    fun `verifyIbanVop - 200 with VOP result`() {
        every { payeeService.verifyIbanVop(userId, payeeId, ibanId) } returns sampleVopResponse

        mockMvc.perform(
            post("/api/association/payees/$payeeId/ibans/$ibanId/verify-vop")
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
            post("/api/association/payees/$payeeId/ibans/$ibanId/verify-vop")
        )
            .andExpect(status().isUnauthorized)
    }
}

package org.commonlink.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.commonlink.dto.MandateDocumentSlotDto
import org.commonlink.dto.MandateStateDto
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.MandateEligibility
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.MandatePdfService
import org.commonlink.service.MandateService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(MandateController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class MandateControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @MockkBean private lateinit var mandateService: MandateService
    @MockkBean private lateinit var mandatePdfService: MandatePdfService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val now = Instant.parse("2026-01-15T10:00:00Z")

    private val unsignedState = MandateStateDto(
        signed = false,
        reference = null,
        signedAt = null,
        eligibility = null,
        revokedAt = null,
        mandateDocs = listOf(
            MandateDocumentSlotDto(AssociationDocumentType.MANDATE_STATUTS, false, null, null, null, null),
            MandateDocumentSlotDto(AssociationDocumentType.MANDATE_RESCRIT, false, null, null, null, null),
        ),
        blocked = false,
    )

    private val signedState = unsignedState.copy(
        signed = true,
        reference = "MND-2026-0001",
        signedAt = now,
        eligibility = MandateEligibility.OIG_66,
    )

    // ── GET /api/association/mandate ─────────────────────────────────────

    @Test
    fun `getMandateState - 200 unsigned`() {
        every { mandateService.getMandateState(userId) } returns unsignedState

        mockMvc.perform(get("/api/association/mandate").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signed").value(false))
            .andExpect(jsonPath("$.blocked").value(false))
            .andExpect(jsonPath("$.mandateDocs").isArray)
            .andExpect(jsonPath("$.mandateDocs.length()").value(2))
    }

    @Test
    fun `getMandateState - 200 signed`() {
        every { mandateService.getMandateState(userId) } returns signedState

        mockMvc.perform(get("/api/association/mandate").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signed").value(true))
            .andExpect(jsonPath("$.reference").value("MND-2026-0001"))
            .andExpect(jsonPath("$.eligibility").value("OIG_66"))
    }

    @Test
    fun `getMandateState - 401 unauthenticated`() {
        mockMvc.perform(get("/api/association/mandate"))
            .andExpect(status().isUnauthorized)
    }

    // ── PUT /api/association/mandate/documents/{docType} ─────────────────

    @Test
    fun `uploadMandateDocument - 204 happy path`() {
        justRun { mandateService.uploadMandateDocument(userId, AssociationDocumentType.MANDATE_STATUTS, any()) }
        val file = MockMultipartFile("file", "statuts.pdf", "application/pdf", "pdf-content".toByteArray())

        mockMvc.perform(
            multipart(HttpMethod.PUT, "/api/association/mandate/documents/MANDATE_STATUTS")
                .file(file)
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `uploadMandateDocument - 409 when active mandate exists`() {
        every {
            mandateService.uploadMandateDocument(userId, AssociationDocumentType.MANDATE_STATUTS, any())
        } throws ConflictException("Cannot replace mandate documents while an active mandate is signed")
        val file = MockMultipartFile("file", "statuts.pdf", "application/pdf", "pdf-content".toByteArray())

        mockMvc.perform(
            multipart(HttpMethod.PUT, "/api/association/mandate/documents/MANDATE_STATUTS")
                .file(file)
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isConflict)
    }

    // ── DELETE /api/association/mandate/documents/{docType} ───────────────

    @Test
    fun `deleteMandateDocument - 204 happy path`() {
        justRun { mandateService.deleteMandateDocument(userId, AssociationDocumentType.MANDATE_RESCRIT) }

        mockMvc.perform(
            delete("/api/association/mandate/documents/MANDATE_RESCRIT")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteMandateDocument - 404 when not found`() {
        every {
            mandateService.deleteMandateDocument(userId, AssociationDocumentType.MANDATE_RESCRIT)
        } throws NotFoundException("Document MANDATE_RESCRIT not found")

        mockMvc.perform(
            delete("/api/association/mandate/documents/MANDATE_RESCRIT")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNotFound)
    }

    // ── POST /api/association/mandate/sign ────────────────────────────────

    @Test
    fun `signMandate - 200 happy path`() {
        every { mandateService.signMandate(userId, any()) } returns signedState
        val body = mapOf("eligibility" to "OIG_66", "accepted" to true)

        mockMvc.perform(
            post("/api/association/mandate/sign")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signed").value(true))
            .andExpect(jsonPath("$.reference").value("MND-2026-0001"))
    }

    @Test
    fun `signMandate - 422 when accepted is false`() {
        every {
            mandateService.signMandate(userId, any())
        } throws UnprocessableEntityException("You must accept the mandate terms")
        val body = mapOf("eligibility" to "OIG_66", "accepted" to false)

        mockMvc.perform(
            post("/api/association/mandate/sign")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `signMandate - 409 when not VERIFIED`() {
        every {
            mandateService.signMandate(userId, any())
        } throws ConflictException("Association must be VERIFIED before signing a mandate")
        val body = mapOf("eligibility" to "OIG_66", "accepted" to true)

        mockMvc.perform(
            post("/api/association/mandate/sign")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isConflict)
    }

    // NOTE: the mandate-documents precondition in MandateService.signMandate is temporarily
    // disabled (see SHOW_MANDATE_DOCS on the frontend). This test now only verifies the generic
    // ConflictException → 409 mapping via a mocked service; restore the docs-specific scenario
    // when the guard comes back.
    @Test
    fun `signMandate - 409 on service ConflictException (docs guard temporarily disabled)`() {
        every {
            mandateService.signMandate(userId, any())
        } throws ConflictException("An active mandate already exists; revoke it before re-signing")
        val body = mapOf("eligibility" to "OIG_75_COLUCHE", "accepted" to true)

        mockMvc.perform(
            post("/api/association/mandate/sign")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `signMandate - 409 when active mandate already exists`() {
        every {
            mandateService.signMandate(userId, any())
        } throws ConflictException("An active mandate already exists")
        val body = mapOf("eligibility" to "OIG_66", "accepted" to true)

        mockMvc.perform(
            post("/api/association/mandate/sign")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `signMandate - 422 when eligibility is null`() {
        val body = mapOf("accepted" to true)

        mockMvc.perform(
            post("/api/association/mandate/sign")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isUnprocessableEntity)
    }

    // ── POST /api/association/mandate/revoke ──────────────────────────────

    @Test
    fun `revokeMandate - 204 happy path`() {
        justRun { mandateService.revokeMandate(userId) }

        mockMvc.perform(
            post("/api/association/mandate/revoke")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `revokeMandate - 404 when no active mandate`() {
        every { mandateService.revokeMandate(userId) } throws NotFoundException("No active mandate found")

        mockMvc.perform(
            post("/api/association/mandate/revoke")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `revokeMandate - 401 unauthenticated`() {
        mockMvc.perform(post("/api/association/mandate/revoke"))
            .andExpect(status().isUnauthorized)
    }

    // ── GET /api/association/mandate/pdf ─────────────────────────────────

    @Test
    fun `downloadMandatePdf - 200 returns PDF bytes`() {
        val fakeMandate = mockk<FiscalMandate>(relaxed = true) {
            every { reference } returns "MND-2026-0001"
        }
        val fakeProfile = mockk<AssociationProfile>(relaxed = true)
        val pdfBytes = "%PDF-1.4 fake content".toByteArray()

        every { mandateService.getMandatePdf(userId) } returns Pair(fakeMandate, fakeProfile)
        every { mandatePdfService.generate(fakeMandate, fakeProfile) } returns pdfBytes

        mockMvc.perform(get("/api/association/mandate/pdf").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"mandat-MND-2026-0001.pdf\""))
    }

    @Test
    fun `downloadMandatePdf - 404 when no active mandate`() {
        every { mandateService.getMandatePdf(userId) } throws NotFoundException("No active mandate found")

        mockMvc.perform(get("/api/association/mandate/pdf").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isNotFound)
    }

    // ── re-sign after revocation ──────────────────────────────────────────

    @Test
    fun `signMandate after revocation - 200 with new reference`() {
        val newSignedState = signedState.copy(reference = "MND-2026-0002")
        every { mandateService.signMandate(userId, any()) } returns newSignedState
        val body = mapOf("eligibility" to "PUBLIC_UTILITY_66", "accepted" to true)

        mockMvc.perform(
            post("/api/association/mandate/sign")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reference").value("MND-2026-0002"))
    }
}

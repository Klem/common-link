package org.commonlink.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.commonlink.dto.AdminVerificationDetailDto
import org.commonlink.dto.AdminVerificationSummaryDto
import org.commonlink.dto.DocumentSlotDto
import org.commonlink.dto.RegistryPreCheckDto
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.ScopeVerdict
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationDocumentMetadata
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AssociationRegistryCheckService
import org.commonlink.service.VerificationService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(AdminVerificationController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AdminVerificationControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @MockkBean private lateinit var verificationService: VerificationService
    @MockkBean private lateinit var registryCheckService: AssociationRegistryCheckService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val associationId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val docId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val now = Instant.parse("2026-01-15T10:00:00Z")

    private val sampleSummary = AdminVerificationSummaryDto(
        associationId = associationId,
        name = "Association Test",
        identifier = "123456789",
        status = VerificationStatus.PENDING,
        submittedAt = now,
        docCount = 2,
    )

    private val sampleDetail = AdminVerificationDetailDto(
        associationId = associationId,
        name = "Association Test",
        identifier = "123456789",
        status = VerificationStatus.PENDING,
        rejectionReason = null,
        submittedAt = now,
        verifiedAt = null,
        docCount = 2,
        requiredDocuments = listOf(
            DocumentSlotDto(AssociationDocumentType.VERIF_STATUTS, true, docId, "statuts.pdf", 12345L, now),
            DocumentSlotDto(AssociationDocumentType.VERIF_RNA_RECEIPT, true, UUID.randomUUID(), "rna.pdf", 9876L, now),
            DocumentSlotDto(AssociationDocumentType.VERIF_REPRESENTATIVE_ID, false, null, null, null, null),
        ),
        optionalDocuments = emptyList(),
    )

    // -------------------------------------------------------------------------
    // GET /api/admin/verifications
    // -------------------------------------------------------------------------

    @Test
    fun `listVerifications - 200 with CURATOR role`() {
        every { verificationService.adminListVerifications(VerificationStatus.PENDING, any<Pageable>()) } returns
            PageImpl(listOf(sampleSummary))

        mockMvc.perform(get("/api/admin/verifications").with(user("curator").roles("CURATOR")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content[0].associationId").value(associationId.toString()))
            .andExpect(jsonPath("$.content[0].status").value("PENDING"))
    }

    @Test
    fun `listVerifications - 403 with ASSOCIATION role`() {
        mockMvc.perform(get("/api/admin/verifications").with(user("assoc").roles("ASSOCIATION")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `listVerifications - 401 when unauthenticated`() {
        mockMvc.perform(get("/api/admin/verifications"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `listVerifications - accepts custom status param`() {
        every { verificationService.adminListVerifications(VerificationStatus.VERIFIED, any<Pageable>()) } returns
            PageImpl(emptyList())

        mockMvc.perform(
            get("/api/admin/verifications").param("status", "VERIFIED").with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/verifications/{associationId}
    // -------------------------------------------------------------------------

    @Test
    fun `getDetail - 200 with CURATOR role`() {
        every { verificationService.adminGetDetail(associationId) } returns sampleDetail

        mockMvc.perform(
            get("/api/admin/verifications/$associationId").with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.associationId").value(associationId.toString()))
            .andExpect(jsonPath("$.requiredDocuments").isArray)
            .andExpect(jsonPath("$.requiredDocuments.length()").value(3))
    }

    @Test
    fun `getDetail - 404 when association not found`() {
        every { verificationService.adminGetDetail(associationId) } throws
            NotFoundException("Association $associationId not found")

        mockMvc.perform(
            get("/api/admin/verifications/$associationId").with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isNotFound)
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/verifications/{associationId}/documents/{docId}/content
    // -------------------------------------------------------------------------

    @Test
    fun `downloadDocument - 200 returns binary content`() {
        val meta = object : AssociationDocumentMetadata {
            override val id = docId
            override val docType = AssociationDocumentType.VERIF_STATUTS
            override val category: String? = null
            override val fileName = "statuts.pdf"
            override val contentType = "application/pdf"
            override val sizeBytes = 5L
            override val uploadedAt = now
        }
        every { verificationService.adminDownloadDocument(associationId, docId) } returns (meta to "PDF".toByteArray())

        mockMvc.perform(
            get("/api/admin/verifications/$associationId/documents/$docId/content")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `downloadDocument - 404 when document not found`() {
        every { verificationService.adminDownloadDocument(associationId, docId) } throws
            NotFoundException("Document $docId not found")

        mockMvc.perform(
            get("/api/admin/verifications/$associationId/documents/$docId/content")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isNotFound)
    }

    // -------------------------------------------------------------------------
    // GET/POST /api/admin/verifications/{associationId}/registry-precheck
    // -------------------------------------------------------------------------

    private val samplePreCheck = RegistryPreCheckDto(
        id = UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
        associationExists = true,
        siren = "123456789",
        rna = "W123456789",
        etatAdministratif = "A",
        joafeDeclarationFound = true,
        dissolutionDetected = false,
        bodaccProcedureFound = false,
        checkedAt = now,
        warnings = emptyList(),
        officers = emptyList(),
        rnaActive = null,
        legalCategory = "9220",
        scopeVerdict = ScopeVerdict.IN_SCOPE,
    )

    @Test
    fun `latestRegistryPreCheck - 200 when a scan exists`() {
        every { registryCheckService.latest(associationId) } returns samplePreCheck

        mockMvc.perform(
            get("/api/admin/verifications/$associationId/registry-precheck")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(samplePreCheck.id.toString()))
            .andExpect(jsonPath("$.siren").value("123456789"))
    }

    @Test
    fun `latestRegistryPreCheck - 204 when never scanned`() {
        every { registryCheckService.latest(associationId) } returns null

        mockMvc.perform(
            get("/api/admin/verifications/$associationId/registry-precheck")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `scanRegistryPreCheck - 200 runs and persists a scan`() {
        every { registryCheckService.scan(associationId, any()) } returns samplePreCheck

        // Principal username must be a UUID — the controller resolves the curator id from it.
        mockMvc.perform(
            post("/api/admin/verifications/$associationId/registry-precheck")
                .with(user(associationId.toString()).roles("CURATOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(samplePreCheck.id.toString()))
    }

    @Test
    fun `scanRegistryPreCheck - 403 with ASSOCIATION role`() {
        mockMvc.perform(
            post("/api/admin/verifications/$associationId/registry-precheck")
                .with(user(associationId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isForbidden)
    }

    // -------------------------------------------------------------------------
    // POST /api/admin/verifications/{associationId}/approve
    // -------------------------------------------------------------------------

    @Test
    fun `approve - 204 happy path`() {
        justRun { verificationService.adminApprove(associationId) }

        mockMvc.perform(
            post("/api/admin/verifications/$associationId/approve")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `approve - 409 when not PENDING`() {
        every { verificationService.adminApprove(associationId) } throws
            ConflictException("Cannot approve: status is VERIFIED, expected PENDING")

        mockMvc.perform(
            post("/api/admin/verifications/$associationId/approve")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `approve - 403 with ASSOCIATION role`() {
        mockMvc.perform(
            post("/api/admin/verifications/$associationId/approve")
                .with(user("assoc").roles("ASSOCIATION"))
        )
            .andExpect(status().isForbidden)
    }

    // -------------------------------------------------------------------------
    // POST /api/admin/verifications/{associationId}/reject
    // -------------------------------------------------------------------------

    @Test
    fun `reject - 204 happy path`() {
        justRun { verificationService.adminReject(associationId, "Document illisible") }

        mockMvc.perform(
            post("/api/admin/verifications/$associationId/reject")
                .with(user("curator").roles("CURATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "Document illisible")))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `reject - 409 when not PENDING`() {
        every { verificationService.adminReject(associationId, any()) } throws
            ConflictException("Cannot reject: status is REJECTED, expected PENDING")

        mockMvc.perform(
            post("/api/admin/verifications/$associationId/reject")
                .with(user("curator").roles("CURATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "some reason")))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `reject - 422 when reason is blank`() {
        mockMvc.perform(
            post("/api/admin/verifications/$associationId/reject")
                .with(user("curator").roles("CURATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "")))
        )
            .andExpect(status().`is`(422))
    }

    @Test
    fun `reject - 403 with ASSOCIATION role`() {
        mockMvc.perform(
            post("/api/admin/verifications/$associationId/reject")
                .with(user("assoc").roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "reason")))
        )
            .andExpect(status().isForbidden)
    }
}

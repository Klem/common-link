package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.commonlink.dto.DocumentSlotDto
import org.commonlink.dto.VerificationStateDto
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.VerificationService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(VerificationController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class VerificationControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var verificationService: VerificationService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val sampleState = VerificationStateDto(
        status = VerificationStatus.UNVERIFIED,
        rejectionReason = null,
        submittedAt = null,
        verifiedAt = null,
        requiredDocuments = listOf(
            DocumentSlotDto(AssociationDocumentType.VERIF_STATUTS, false, null, null, null, null),
            DocumentSlotDto(AssociationDocumentType.VERIF_RNA_RECEIPT, false, null, null, null, null),
            DocumentSlotDto(AssociationDocumentType.VERIF_REPRESENTATIVE_ID, false, null, null, null, null),
        )
    )

    // -------------------------------------------------------------------------
    // GET /api/association/verification
    // -------------------------------------------------------------------------

    @Test
    fun `getVerificationState - 200 when authenticated`() {
        every { verificationService.getVerificationState(userId) } returns sampleState

        mockMvc.perform(get("/api/association/verification").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UNVERIFIED"))
            .andExpect(jsonPath("$.requiredDocuments").isArray)
            .andExpect(jsonPath("$.requiredDocuments.length()").value(3))
    }

    @Test
    fun `getVerificationState - 401 when not authenticated`() {
        mockMvc.perform(get("/api/association/verification"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getVerificationState - includes rejection reason when REJECTED`() {
        val rejected = sampleState.copy(
            status = VerificationStatus.REJECTED,
            rejectionReason = "Document illisible"
        )
        every { verificationService.getVerificationState(userId) } returns rejected

        mockMvc.perform(get("/api/association/verification").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("Document illisible"))
    }

    // -------------------------------------------------------------------------
    // PUT /api/association/verification/documents/{docType}
    // -------------------------------------------------------------------------

    private fun pdfFile(name: String = "statuts.pdf") =
        MockMultipartFile("file", name, "application/pdf", "PDF content".toByteArray())

    @Test
    fun `uploadVerificationDocument - 204 when valid file and UNVERIFIED`() {
        justRun { verificationService.uploadVerificationDocument(userId, AssociationDocumentType.VERIF_STATUTS, any()) }

        mockMvc.perform(
            multipart(HttpMethod.PUT, "/api/association/verification/documents/VERIF_STATUTS")
                .file(pdfFile())
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `uploadVerificationDocument - 409 when PENDING`() {
        every {
            verificationService.uploadVerificationDocument(userId, AssociationDocumentType.VERIF_STATUTS, any())
        } throws ConflictException("Cannot modify documents while verification is pending")

        mockMvc.perform(
            multipart(HttpMethod.PUT, "/api/association/verification/documents/VERIF_STATUTS")
                .file(pdfFile())
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `uploadVerificationDocument - 422 when invalid MIME type`() {
        every {
            verificationService.uploadVerificationDocument(userId, AssociationDocumentType.VERIF_STATUTS, any())
        } throws UnprocessableEntityException("File type 'text/plain' is not allowed")

        val txtFile = MockMultipartFile("file", "doc.txt", "text/plain", "content".toByteArray())
        mockMvc.perform(
            multipart(HttpMethod.PUT, "/api/association/verification/documents/VERIF_STATUTS")
                .file(txtFile)
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().`is`(422))
    }

    @Test
    fun `uploadVerificationDocument - 401 when not authenticated`() {
        mockMvc.perform(
            multipart(HttpMethod.PUT, "/api/association/verification/documents/VERIF_STATUTS")
                .file(pdfFile())
        )
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // DELETE /api/association/verification/documents/{docType}
    // -------------------------------------------------------------------------

    @Test
    fun `deleteVerificationDocument - 204 when document exists`() {
        justRun { verificationService.deleteVerificationDocument(userId, AssociationDocumentType.VERIF_STATUTS) }

        mockMvc.perform(
            delete("/api/association/verification/documents/VERIF_STATUTS")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteVerificationDocument - 404 when document not found`() {
        every {
            verificationService.deleteVerificationDocument(userId, AssociationDocumentType.VERIF_STATUTS)
        } throws NotFoundException("Document VERIF_STATUTS not found")

        mockMvc.perform(
            delete("/api/association/verification/documents/VERIF_STATUTS")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deleteVerificationDocument - 409 when PENDING`() {
        every {
            verificationService.deleteVerificationDocument(userId, AssociationDocumentType.VERIF_STATUTS)
        } throws ConflictException("Cannot modify documents while verification is pending")

        mockMvc.perform(
            delete("/api/association/verification/documents/VERIF_STATUTS")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isConflict)
    }

    // -------------------------------------------------------------------------
    // POST /api/association/verification/submit
    // -------------------------------------------------------------------------

    @Test
    fun `submitVerification - 204 when all 3 docs present and UNVERIFIED`() {
        justRun { verificationService.submitVerification(userId) }

        mockMvc.perform(
            post("/api/association/verification/submit")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `submitVerification - 409 when documents missing`() {
        every { verificationService.submitVerification(userId) } throws
            ConflictException("All 3 required documents must be uploaded before submission (2/3 present)")

        mockMvc.perform(
            post("/api/association/verification/submit")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `submitVerification - 409 when already PENDING`() {
        every { verificationService.submitVerification(userId) } throws
            ConflictException("Verification is already pending")

        mockMvc.perform(
            post("/api/association/verification/submit")
                .with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `submitVerification - 401 when not authenticated`() {
        mockMvc.perform(
            post("/api/association/verification/submit")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isUnauthorized)
    }
}

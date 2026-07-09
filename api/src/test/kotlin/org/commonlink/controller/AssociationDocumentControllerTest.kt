package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.commonlink.dto.OptionalDocumentDto
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationDocumentMetadata
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(AssociationDocumentController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AssociationDocumentControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var verificationService: VerificationService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val docId  = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val sampleDto = OptionalDocumentDto(
        id = docId,
        fileName = "rapport-2024.pdf",
        category = "REPORT",
        contentType = "application/pdf",
        sizeBytes = 1_200_000L,
        uploadedAt = Instant.parse("2025-01-10T10:00:00Z"),
    )

    private fun pdfFile(name: String = "rapport.pdf") =
        MockMultipartFile("file", name, "application/pdf", "PDF content".toByteArray())

    // -------------------------------------------------------------------------
    // GET /api/association/documents
    // -------------------------------------------------------------------------

    @Test
    fun `listDocuments - 200 with list when authenticated`() {
        every { verificationService.listOptionalDocuments(userId) } returns listOf(sampleDto)

        mockMvc.perform(get("/api/association/documents").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(docId.toString()))
            .andExpect(jsonPath("$[0].fileName").value("rapport-2024.pdf"))
            .andExpect(jsonPath("$[0].category").value("REPORT"))
    }

    @Test
    fun `listDocuments - 200 empty list when no documents`() {
        every { verificationService.listOptionalDocuments(userId) } returns emptyList()

        mockMvc.perform(get("/api/association/documents").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `listDocuments - 401 when not authenticated`() {
        mockMvc.perform(get("/api/association/documents"))
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // POST /api/association/documents
    // -------------------------------------------------------------------------

    @Test
    fun `uploadDocument - 201 with valid file and category`() {
        every { verificationService.uploadOptionalDocument(userId, any(), "REPORT") } returns sampleDto

        mockMvc.perform(
            multipart("/api/association/documents")
                .file(pdfFile())
                .param("category", "REPORT")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(docId.toString()))
            .andExpect(jsonPath("$.category").value("REPORT"))
    }

    @Test
    fun `uploadDocument - 201 with default category OTHER`() {
        every { verificationService.uploadOptionalDocument(userId, any(), "OTHER") } returns
            sampleDto.copy(category = "OTHER")

        mockMvc.perform(
            multipart("/api/association/documents")
                .file(pdfFile())
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `uploadDocument - 422 when invalid MIME type`() {
        every { verificationService.uploadOptionalDocument(userId, any(), any()) } throws
            UnprocessableEntityException("File type 'text/plain' is not allowed")

        val txtFile = MockMultipartFile("file", "doc.txt", "text/plain", "content".toByteArray())
        mockMvc.perform(
            multipart("/api/association/documents")
                .file(txtFile)
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().`is`(422))
    }

    @Test
    fun `uploadDocument - 401 when not authenticated`() {
        mockMvc.perform(multipart("/api/association/documents").file(pdfFile()))
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // DELETE /api/association/documents/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `deleteDocument - 204 when document exists and owned`() {
        justRun { verificationService.deleteOptionalDocument(userId, docId) }

        mockMvc.perform(
            delete("/api/association/documents/$docId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteDocument - 404 when document not found`() {
        every { verificationService.deleteOptionalDocument(userId, docId) } throws
            NotFoundException("Document $docId not found")

        mockMvc.perform(
            delete("/api/association/documents/$docId")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deleteDocument - 401 when not authenticated`() {
        mockMvc.perform(delete("/api/association/documents/$docId"))
            .andExpect(status().isUnauthorized)
    }

    // -------------------------------------------------------------------------
    // GET /api/association/documents/{id}/content
    // -------------------------------------------------------------------------

    @Test
    fun `downloadDocument - 200 with binary content and headers`() {
        val fakeContent = "PDF binary content".toByteArray()
        val fakeMeta = object : AssociationDocumentMetadata {
            override val id = docId
            override val docType = org.commonlink.entity.AssociationDocumentType.OPTIONAL
            override val category = "REPORT"
            override val fileName = "rapport-2024.pdf"
            override val contentType = "application/pdf"
            override val sizeBytes = fakeContent.size.toLong()
            override val uploadedAt: Instant = Instant.parse("2025-01-10T10:00:00Z")
        }
        every { verificationService.downloadDocument(userId, docId) } returns (fakeMeta to fakeContent)

        mockMvc.perform(
            get("/api/association/documents/$docId/content")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("rapport-2024.pdf")))
    }

    @Test
    fun `downloadDocument - 404 when document not found`() {
        every { verificationService.downloadDocument(userId, docId) } throws
            NotFoundException("Document $docId not found")

        mockMvc.perform(
            get("/api/association/documents/$docId/content")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isNotFound)
    }
}

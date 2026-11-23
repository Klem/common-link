package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.SignatoryDto
import org.commonlink.entity.SignatureMode
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.SignatoryService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(SignatoryController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class SignatoryControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var signatoryService: SignatoryService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val now = Instant.parse("2026-11-23T10:00:00Z")
    private val url = "/api/association/me/signatory"

    private val savedDto = SignatoryDto(
        signerName = "Marie Dupont",
        signerRole = "Présidente",
        mode = SignatureMode.DRAWN,
        hasSignature = true,
        attestedAt = now,
        updatedAt = now,
    )

    private val pngFile = MockMultipartFile("file", "signature.png", "image/png", byteArrayOf(1, 2, 3))

    private fun putSignatory(file: MockMultipartFile? = pngFile) =
        multipart(HttpMethod.PUT, url).apply { if (file != null) this.file(file) }
            .param("signerName", "Marie Dupont")
            .param("signerRole", "Présidente")
            .param("mode", "DRAWN")
            .param("attested", "true")

    // ── Security ───────────────────────────────────────────────────────────

    @Test
    fun `401 without JWT`() {
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized)
        mockMvc.perform(putSignatory()).andExpect(status().isUnauthorized)
    }

    @Test
    fun `403 for a donor`() {
        mockMvc.perform(get(url).with(user(userId.toString()).roles("DONOR"))).andExpect(status().isForbidden)
        mockMvc.perform(get("$url/image").with(user(userId.toString()).roles("DONOR"))).andExpect(status().isForbidden)
        mockMvc.perform(putSignatory().with(user(userId.toString()).roles("DONOR"))).andExpect(status().isForbidden)
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test
    fun `get returns the signatory`() {
        every { signatoryService.getSignatory(userId) } returns
            SignatoryDto("Marie Dupont", "Présidente", null, false, null, null)

        mockMvc.perform(get(url).with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signerName").value("Marie Dupont"))
            .andExpect(jsonPath("$.signerRole").value("Présidente"))
            .andExpect(jsonPath("$.mode").isEmpty)
            .andExpect(jsonPath("$.hasSignature").value(false))
    }

    @Test
    fun `get image returns the bytes with their content type, never cached`() {
        every { signatoryService.getSignatureImage(userId) } returns ("image/png" to byteArrayOf(9, 8, 7))

        mockMvc.perform(get("$url/image").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().bytes(byteArrayOf(9, 8, 7)))
    }

    @Test
    fun `get image is 404 without image`() {
        every { signatoryService.getSignatureImage(userId) } throws NotFoundException("No signature image")

        mockMvc.perform(get("$url/image").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isNotFound)
    }

    // ── PUT ──────────────────────────────────────────────────────────────────

    @Test
    fun `put forwards every multipart field and returns the saved signatory`() {
        every {
            signatoryService.saveSignatory(userId, "Marie Dupont", "Présidente", "DRAWN", "true", any())
        } returns savedDto

        mockMvc.perform(putSignatory().with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("DRAWN"))
            .andExpect(jsonPath("$.hasSignature").value(true))
            .andExpect(jsonPath("$.attestedAt").exists())

        verify(exactly = 1) {
            signatoryService.saveSignatory(userId, "Marie Dupont", "Présidente", "DRAWN", "true", match { it!!.size == 3L })
        }
    }

    /** The file is required for every mode: a missing file reaches the service, which answers 422. */
    @Test
    fun `put without file reaches the service with a null file and is refused`() {
        every { signatoryService.saveSignatory(userId, any(), any(), any(), any(), null) } throws
            UnprocessableEntityException("A signature image file is required")

        mockMvc.perform(putSignatory(file = null).with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `put answers 422 on any invalid input`() {
        every { signatoryService.saveSignatory(any(), any(), any(), any(), any(), any()) } throws
            UnprocessableEntityException("The signatory attestation must be accepted")

        mockMvc.perform(putSignatory().with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isUnprocessableContent)
    }

    /** Missing fields are not a Spring 400: the service owns validation and answers 422 like any other refusal. */
    @Test
    fun `put with missing fields still reaches the service`() {
        every { signatoryService.saveSignatory(userId, null, null, null, null, null) } throws
            UnprocessableEntityException("Signer name is required")

        mockMvc.perform(multipart(HttpMethod.PUT, url).with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isUnprocessableContent)
    }
}

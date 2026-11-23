package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationSignature
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.SignatureMode
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationSignatureRepository
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockMultipartFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.Optional
import java.util.UUID
import javax.imageio.ImageIO

class SignatoryServiceTest {

    private val profileRepository = mockk<AssociationProfileRepository>()
    private val signatureRepository = mockk<AssociationSignatureRepository>()
    private val auditLog = mockk<ComplianceAuditLogService>()
    private val service = SignatoryService(profileRepository, signatureRepository, auditLog).also {
        it.clock = Clock.fixed(Instant.parse("2026-11-23T10:00:00.123456789Z"), ZoneOffset.UTC)
    }

    private val userId: UUID = UUID.randomUUID()
    private val associationId: UUID = UUID.randomUUID()
    private val profile = AssociationProfile(
        id = associationId,
        user = User(email = "a@b.fr", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL),
        name = "Asso",
        identifier = "W123456789",
    )
    private val savedSignature = slot<AssociationSignature>()
    private val payload = slot<Any>()

    @BeforeEach
    fun setUp() {
        every { profileRepository.findByUserId(userId) } returns Optional.of(profile)
        every { profileRepository.save(profile) } returns profile
        every { signatureRepository.findById(associationId) } returns Optional.empty()
        every { signatureRepository.save(capture(savedSignature)) } answers { firstArg() }
        every { auditLog.append(any(), any(), capture(payload), any(), any()) } returns mockk()
    }

    private fun png(width: Int = 400, height: Int = 150, format: String = "png"): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, width, height)
            color = Color.BLACK
            drawLine(0, height - 1, width - 1, 0)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()
    }

    private fun file(bytes: ByteArray, contentType: String = "image/png") =
        MockMultipartFile("file", "signature.png", contentType, bytes)

    private fun save(
        name: String? = "  Marie Dupont ",
        role: String? = " Présidente ",
        mode: String? = "UPLOAD",
        attested: String? = "true",
        file: MockMultipartFile? = file(png()),
    ) = service.saveSignatory(userId, name, role, mode, attested, file)

    private fun assertRejected(block: () -> Unit): UnprocessableEntityException {
        val ex = assertThrows<UnprocessableEntityException> { block() }
        verify(exactly = 0) { signatureRepository.save(any()) }
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
        return ex
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    fun `refuses when the attestation is not given`() {
        assertRejected { save(attested = "false") }
        assertRejected { save(attested = null) }
    }

    @Test
    fun `refuses a blank or too long signer name or role`() {
        assertRejected { save(name = "   ") }
        assertRejected { save(name = "x".repeat(256)) }
        assertRejected { save(role = null) }
        assertRejected { save(role = "x".repeat(101)) }
    }

    /** TYPED (signer name in a handwriting font) was withdrawn: it is now an unknown mode like any other. */
    @Test
    fun `refuses an unknown mode, the withdrawn TYPED mode included`() {
        assertRejected { save(mode = "SCANNED") }
        assertRejected { save(mode = null) }
        assertRejected { save(mode = "TYPED") }
        assertRejected { save(mode = "TYPED", file = null) }
    }

    @Test
    fun `refuses an upload or a drawing without file`() {
        assertRejected { save(mode = "UPLOAD", file = null) }
        assertRejected { save(mode = "DRAWN", file = file(ByteArray(0))) }
    }

    @Test
    fun `refuses bytes that are neither PNG nor JPEG whatever the declared type`() {
        assertRejected { save(file = file("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray())) }
        assertRejected { save(file = file("GIF89a......".toByteArray())) }
        // RIFF....WEBP: a real WebP header, accepted for logos but not embeddable in the receipt PDF.
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8 ".toByteArray()
        assertRejected { save(file = file(webp, "image/webp")) }
    }

    @Test
    fun `refuses a file over 200 KB`() {
        val ex = assertRejected { save(file = file(png() + ByteArray(204_800))) }
        assertTrue(ex.message!!.contains("200 KB"), ex.message)
    }

    @Test
    fun `refuses an image over the pixel bounds`() {
        assertRejected { save(file = file(png(width = 1201, height = 100))) }
        assertRejected { save(file = file(png(width = 300, height = 601))) }
    }

    @Test
    fun `refuses a truncated image`() {
        val truncated = png().copyOf(60)
        assertRejected { save(file = file(truncated)) }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    fun `saves an uploaded signature, the trimmed signer and journals the attestation with the image hash`() {
        val bytes = png()

        val dto = save(file = file(bytes, contentType = "application/octet-stream"))

        assertEquals("Marie Dupont", profile.signerName)
        assertEquals("Présidente", profile.signerRole)
        val signature = savedSignature.captured
        assertEquals(SignatureMode.UPLOAD, signature.mode)
        assertArrayEquals(bytes, signature.image)
        assertEquals("image/png", signature.contentType, "Content type must be sniffed, not the declared one")
        val now = Instant.parse("2026-11-23T10:00:00.123456Z")
        assertEquals(now, signature.attestedAt)
        assertEquals(now, signature.updatedAt)

        verify(exactly = 1) {
            auditLog.append(
                ComplianceAuditLogService.RECEIPT_SIGNATURE_ATTESTED,
                ComplianceAuditSubjectType.ASSOCIATION,
                any(),
                associationId,
                userId,
            )
        }
        @Suppress("UNCHECKED_CAST")
        val journaled = payload.captured as Map<String, Any?>
        assertEquals("Marie Dupont", journaled["signerName"])
        assertEquals("Présidente", journaled["signerRole"])
        assertEquals("UPLOAD", journaled["mode"])
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), journaled["imageSha256"])
        assertEquals(SignatoryService.ATTESTATION_TEXT, journaled["attestation"])

        assertTrue(dto.hasSignature)
        assertEquals(SignatureMode.UPLOAD, dto.mode)
        assertEquals(now, dto.attestedAt)
    }

    @Test
    fun `saves a drawn JPEG signature`() {
        save(mode = "DRAWN", file = file(png(format = "jpg"), "image/jpeg"))

        assertEquals(SignatureMode.DRAWN, savedSignature.captured.mode)
        assertEquals("image/jpeg", savedSignature.captured.contentType)
    }

    @Test
    fun `replaces the existing signature row`() {
        val old = Instant.parse("2026-01-01T00:00:00Z")
        val existing = AssociationSignature(associationId, SignatureMode.UPLOAD, png(), "image/png", old, old)
        every { signatureRepository.findById(associationId) } returns Optional.of(existing)

        val drawn = png(format = "jpg")
        save(mode = "DRAWN", file = file(drawn, "image/jpeg"))

        assertSame(existing, savedSignature.captured)
        assertEquals(SignatureMode.DRAWN, existing.mode)
        assertArrayEquals(drawn, existing.image)
        assertEquals("image/jpeg", existing.contentType)
        assertEquals(Instant.parse("2026-11-23T10:00:00.123456Z"), existing.attestedAt)
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Test
    fun `reports no signature before the first save`() {
        profile.signerName = "Marie Dupont"

        val dto = service.getSignatory(userId)

        assertFalse(dto.hasSignature)
        assertNull(dto.mode)
        assertNull(dto.attestedAt)
        assertEquals("Marie Dupont", dto.signerName)
    }

    @Test
    fun `serves the stored image with its content type`() {
        val bytes = png()
        val now = Instant.now()
        every { signatureRepository.findById(associationId) } returns
            Optional.of(AssociationSignature(associationId, SignatureMode.DRAWN, bytes, "image/png", now, now))

        val (contentType, served) = service.getSignatureImage(userId)

        assertEquals("image/png", contentType)
        assertArrayEquals(bytes, served)
    }

    @Test
    fun `has no image to serve without signature`() {
        assertThrows<NotFoundException> { service.getSignatureImage(userId) }
    }
}

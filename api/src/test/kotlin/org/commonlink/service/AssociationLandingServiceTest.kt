package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.dto.UpdateLandingConfigRequest
import org.commonlink.entity.AssociationLogo
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.LandingTheme
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationLogoRepository
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.TestFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockMultipartFile
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Plain mockk unit test for AssociationLandingService — no Spring context.
 */
class AssociationLandingServiceTest {

    private val associationRepo: AssociationProfileRepository = mockk()
    private val logoRepo: AssociationLogoRepository = mockk()
    // Relaxed: the bank-ready guard is a no-op here; its behaviour is covered by OnboardingGateService tests.
    private val onboardingGate: OnboardingGateService = mockk(relaxed = true)

    // Real token service: signing is deterministic and self-contained, mocking it would only assert a mock.
    private val previewTokenService = LandingPreviewTokenService("test-secret-key-must-be-at-least-32-chars!!")

    private val service =
        AssociationLandingService(associationRepo, logoRepo, onboardingGate, previewTokenService)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val assocId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun profile() = AssociationProfile(
        id = assocId,
        user = mockk(relaxed = true),
        name = "Asso Test",
        identifier = "W123456789",
    )

    private fun stubProfile(profile: AssociationProfile) {
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        every { associationRepo.save(profile) } returns profile
    }

    // ── Landing config ────────────────────────────────────────────────────────

    @Test
    fun `updateLandingConfig - sets the theme`() {
        val profile = profile()
        stubProfile(profile)

        val dto = service.updateLandingConfig(userId, UpdateLandingConfigRequest(theme = LandingTheme.NATURE))

        assertEquals(LandingTheme.NATURE, dto.landingTheme)
    }

    @Test
    fun `updateLandingConfig - empty request changes nothing`() {
        val profile = profile().apply { landingTheme = LandingTheme.WARM }
        stubProfile(profile)

        val dto = service.updateLandingConfig(userId, UpdateLandingConfigRequest())

        // A partial PATCH must never reset the fields it does not carry.
        assertEquals(LandingTheme.WARM, dto.landingTheme)
    }

    @Test
    fun `updateLandingConfig - requires the bank-ready gate`() {
        val profile = profile()
        stubProfile(profile)

        service.updateLandingConfig(userId, UpdateLandingConfigRequest(theme = LandingTheme.NATURE))

        verify(exactly = 1) { onboardingGate.requireBankReady(userId) }
    }

    @Test
    fun `updateLandingConfig - sets the GTM container ID`() {
        val profile = profile()
        stubProfile(profile)

        val dto = service.updateLandingConfig(userId, UpdateLandingConfigRequest(gtmContainerId = "GTM-ABC1234"))

        assertEquals("GTM-ABC1234", dto.gtmContainerId)
    }

    @Test
    fun `updateLandingConfig - blank GTM container ID clears it`() {
        val profile = profile().apply { gtmContainerId = "GTM-ABC1234" }
        stubProfile(profile)

        val dto = service.updateLandingConfig(userId, UpdateLandingConfigRequest(gtmContainerId = ""))

        assertNull(dto.gtmContainerId)
    }

    @Test
    fun `updateLandingConfig - absent GTM container ID leaves it untouched`() {
        val profile = profile().apply { gtmContainerId = "GTM-ABC1234" }
        stubProfile(profile)

        val dto = service.updateLandingConfig(userId, UpdateLandingConfigRequest(theme = LandingTheme.WARM))

        assertEquals("GTM-ABC1234", dto.gtmContainerId)
    }

    @Test
    fun `updateLandingConfig - unknown user throws UserNotFoundException`() {
        every { associationRepo.findByUserId(userId) } returns Optional.empty()

        assertThrows<UserNotFoundException> {
            service.updateLandingConfig(userId, UpdateLandingConfigRequest(theme = LandingTheme.SOBER))
        }
    }

    // ── Preview token ─────────────────────────────────────────────────────────

    @Test
    fun `issuePreviewToken - issues a token scoped to the caller's association`() {
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile())

        val dto = service.issuePreviewToken(userId)

        assertEquals(assocId, previewTokenService.resolveAssociationId(dto.previewToken))
        assertTrue(dto.expiresAt.isAfter(Instant.now()))
        verify(exactly = 1) { onboardingGate.requireBankReady(userId) }
    }

    @Test
    fun `issuePreviewToken - unknown user throws UserNotFoundException`() {
        every { associationRepo.findByUserId(userId) } returns Optional.empty()

        assertThrows<UserNotFoundException> { service.issuePreviewToken(userId) }
    }

    // ── Logo upload ───────────────────────────────────────────────────────────

    @Test
    fun `uploadLogo - stores the bytes and the public serving path`() {
        val profile = profile()
        stubProfile(profile)
        val saved = slot<AssociationLogo>()
        every { logoRepo.save(capture(saved)) } answers { saved.captured }

        val file = MockMultipartFile("file", "logo.png", "image/png", TestFiles.png(padding = 120))
        val dto = service.uploadLogo(userId, file)

        assertEquals("/api/public/associations/$assocId/logo", dto.landingLogo)
        assertEquals(assocId, saved.captured.associationId)
        assertEquals("image/png", saved.captured.contentType)
        assertEquals(128L, saved.captured.sizeBytes)
    }

    @Test
    fun `uploadLogo - empty file is rejected`() {
        stubProfile(profile())

        val file = MockMultipartFile("file", "logo.png", "image/png", ByteArray(0))

        assertThrows<UnprocessableEntityException> { service.uploadLogo(userId, file) }
    }

    @Test
    fun `uploadLogo - oversized file is rejected`() {
        stubProfile(profile())

        val tooBig = ByteArray((AssociationLandingService.MAX_LOGO_SIZE + 1).toInt())
        val file = MockMultipartFile("file", "logo.png", "image/png", tooBig)

        assertThrows<UnprocessableEntityException> { service.uploadLogo(userId, file) }
    }

    @Test
    fun `uploadLogo - SVG is rejected`() {
        stubProfile(profile())

        val file = MockMultipartFile("file", "logo.svg", "image/svg+xml", "<svg/>".toByteArray())

        assertThrows<UnprocessableEntityException> { service.uploadLogo(userId, file) }
    }

    /**
     * Security regression (audit 2026-08-20, M9): the declared type is caller-controlled and these
     * bytes are served back verbatim from a public endpoint, so a payload contradicting its own
     * declaration must not be stored.
     */
    @Test
    fun `uploadLogo - bytes contradicting the declared type are rejected`() {
        stubProfile(profile())

        val file = MockMultipartFile("file", "logo.png", "image/png", TestFiles.mislabelled())

        assertThrows<UnprocessableEntityException> { service.uploadLogo(userId, file) }
    }

    // ── Logo deletion and serving ─────────────────────────────────────────────

    @Test
    fun `deleteLogo - clears the path and removes the row`() {
        val profile = profile().apply { landingLogo = "/api/public/associations/$assocId/logo" }
        stubProfile(profile)
        justRun { logoRepo.deleteById(assocId) }

        val dto = service.deleteLogo(userId)

        assertNull(dto.landingLogo)
        verify(exactly = 1) { logoRepo.deleteById(assocId) }
    }

    @Test
    fun `getLogo - returns content type and bytes`() {
        val bytes = TestFiles.webp()
        every { logoRepo.findById(assocId) } returns Optional.of(
            AssociationLogo(assocId, bytes, "image/webp", bytes.size.toLong(), Instant.now())
        )

        val (contentType, data) = service.getLogo(assocId)

        assertEquals("image/webp", contentType)
        assertEquals(bytes.size, data.size)
    }

    /**
     * The served Content-Type is derived from the bytes, not from the stored declaration
     * (audit 2026-08-20, M9). Pinned on a deliberately contradictory row: rows written before upload
     * byte-validation existed carry a declaration nothing ever checked.
     */
    @Test
    fun `getLogo - Content-Type comes from the bytes, not the stored declaration`() {
        val actuallyPng = TestFiles.png()
        every { logoRepo.findById(assocId) } returns Optional.of(
            AssociationLogo(assocId, actuallyPng, "image/webp", actuallyPng.size.toLong(), Instant.now())
        )

        val (contentType, _) = service.getLogo(assocId)

        assertEquals("image/png", contentType, "Bytes win over the stored column")
    }

    @Test
    fun `getLogo - a legacy row whose bytes are not an image is not served`() {
        every { logoRepo.findById(assocId) } returns Optional.of(
            AssociationLogo(assocId, TestFiles.mislabelled(), "image/png", 38, Instant.now())
        )

        assertThrows<NotFoundException> { service.getLogo(assocId) }
    }

    @Test
    fun `getLogo - is deliberately not ownership-scoped`() {
        // Writes cannot be forged: they resolve the profile from the authenticated userId and take no
        // id parameter. Reads are the opposite by design — an <img> tag carries no Bearer token, so any
        // caller may fetch any association's logo. This test pins that asymmetry so a future
        // "add an ownership check" refactor has to break it on purpose.
        val foreignId = UUID.randomUUID()
        every { logoRepo.findById(foreignId) } returns Optional.of(
            AssociationLogo(foreignId, TestFiles.png(), "image/png", 16, Instant.now())
        )

        val (contentType, _) = service.getLogo(foreignId)

        assertEquals("image/png", contentType)
    }

    @Test
    fun `getLogo - missing logo throws NotFoundException`() {
        every { logoRepo.findById(assocId) } returns Optional.empty()

        assertThrows<NotFoundException> { service.getLogo(assocId) }
    }
}

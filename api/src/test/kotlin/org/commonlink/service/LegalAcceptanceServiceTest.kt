package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.LegalAcceptance
import org.commonlink.entity.LegalAcceptanceSubjectType
import org.commonlink.entity.LegalDocument
import org.commonlink.entity.LegalDocumentType
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.LegalAcceptanceRepository
import org.commonlink.repository.LegalDocumentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class LegalAcceptanceServiceTest {

    private val legalDocumentRepository: LegalDocumentRepository = mockk()
    private val legalAcceptanceRepository: LegalAcceptanceRepository = mockk()
    private val associationProfileRepository: AssociationProfileRepository = mockk()

    private val service = LegalAcceptanceService(
        legalDocumentRepository = legalDocumentRepository,
        legalAcceptanceRepository = legalAcceptanceRepository,
        associationProfileRepository = associationProfileRepository,
    )

    private val associationId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val cguDoc = LegalDocument(documentType = LegalDocumentType.CGU, version = "2026-08-26", content = "text")

    @Test
    fun `currentDocument throws when no document was ever published`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns null

        assertThrows<NotFoundException> { service.currentDocument(LegalDocumentType.CGU) }
    }

    @Test
    fun `requireAssociationAcceptance is a no-op when already accepted the current version`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
                LegalAcceptanceSubjectType.ASSOCIATION, associationId, LegalDocumentType.CGU, "2026-08-26",
            )
        } returns true

        // accepted=false would normally throw — proves the no-op path is genuinely first.
        service.requireAssociationAcceptance(associationId, LegalDocumentType.CGU, accepted = false, "Nom", "a@b.fr", campaignId)

        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `requireAssociationAcceptance throws when not yet accepted and accepted is false`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(any(), any(), any(), any())
        } returns false

        val ex = assertThrows<UnprocessableEntityException> {
            service.requireAssociationAcceptance(associationId, LegalDocumentType.CGU, accepted = false, "Nom", "a@b.fr", campaignId)
        }
        assertTrue(ex.message!!.contains("CGU"))
        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `requireAssociationAcceptance records a snapshot row when accepted is true`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(any(), any(), any(), any())
        } returns false
        val saved = slot<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.captured }

        service.requireAssociationAcceptance(associationId, LegalDocumentType.CGU, accepted = true, "Jean Dupont", "jean@asso.fr", campaignId)

        assertEquals(LegalAcceptanceSubjectType.ASSOCIATION, saved.captured.subjectType)
        assertEquals(associationId, saved.captured.subjectId)
        assertEquals("2026-08-26", saved.captured.documentVersion)
        assertEquals("Jean Dupont", saved.captured.signerName)
        assertEquals("jean@asso.fr", saved.captured.signerEmail)
        assertEquals(campaignId, saved.captured.campaignId)
    }

    @Test
    fun `recordDonorAcceptance writes one row per document type, skipping ones already recorded`() {
        val donorId = UUID.randomUUID()
        val donationId = UUID.randomUUID()
        val cgvDoc = LegalDocument(documentType = LegalDocumentType.CGV, version = "2026-08-26", content = "text")

        every { legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, LegalDocumentType.CGU) } returns false
        every { legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, LegalDocumentType.CGV) } returns true
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGV) } returns cgvDoc
        val saved = slot<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.captured }

        service.recordDonorAcceptance(donorId, donationId, campaignId, "Marie Curie", "marie@example.org")

        verify(exactly = 1) { legalAcceptanceRepository.save(any()) }
        assertEquals(LegalAcceptanceSubjectType.DONOR, saved.captured.subjectType)
        assertEquals(LegalDocumentType.CGU, saved.captured.documentType)
        assertEquals(donationId, saved.captured.donationId)
        assertEquals(campaignId, saved.captured.campaignId)
    }

    @Test
    fun `associationAcceptanceStateForUser resolves the profile then delegates`() {
        val userId = UUID.randomUUID()
        val profile = AssociationProfile(
            id = associationId,
            user = User(email = "a@b.fr", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL),
            name = "Asso",
            identifier = "W123456789",
        )
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(profile)
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
                LegalAcceptanceSubjectType.ASSOCIATION, associationId, LegalDocumentType.CGU, "2026-08-26",
            )
        } returns true

        val state = service.associationAcceptanceStateForUser(userId, LegalDocumentType.CGU)

        assertTrue(state.accepted)
        assertEquals("2026-08-26", state.currentVersion)
    }

    @Test
    fun `associationAcceptanceState reports not accepted when no row exists for the current version`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(any(), any(), any(), any())
        } returns false

        val state = service.associationAcceptanceState(associationId, LegalDocumentType.CGU)

        assertFalse(state.accepted)
    }
}

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
import org.commonlink.exception.LegalDocumentVersionOutdatedException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.LegalAcceptanceRepository
import org.commonlink.repository.LegalDocumentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
        service.requireAssociationAcceptance(associationId, LegalDocumentType.CGU, accepted = false, null, "Nom", "a@b.fr", campaignId)

        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `requireAssociationAcceptance throws when not yet accepted and accepted is false`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(any(), any(), any(), any())
        } returns false

        val ex = assertThrows<UnprocessableEntityException> {
            service.requireAssociationAcceptance(associationId, LegalDocumentType.CGU, accepted = false, null, "Nom", "a@b.fr", campaignId)
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

        service.requireAssociationAcceptance(associationId, LegalDocumentType.CGU, accepted = true, "2026-08-26", "Jean Dupont", "jean@asso.fr", campaignId)

        assertEquals(LegalAcceptanceSubjectType.ASSOCIATION, saved.captured.subjectType)
        assertEquals(associationId, saved.captured.subjectId)
        assertEquals("2026-08-26", saved.captured.documentVersion)
        assertEquals("Jean Dupont", saved.captured.signerName)
        assertEquals("jean@asso.fr", saved.captured.signerEmail)
        assertEquals(campaignId, saved.captured.campaignId)
    }

    @Test
    fun `recordDonorAcceptance is idempotent per donation`() {
        val donationId = UUID.randomUUID()
        every { legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, LegalDocumentType.CGU) } returns true

        service.recordDonorAcceptance(
            UUID.randomUUID(), donationId, campaignId, "Marie Curie", "marie@example.org",
            acceptedVersions = mapOf(LegalDocumentType.CGU to "2026-08-26"),
        )

        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `recordDonorAcceptance writes a single CGU row, never a CGV one`() {
        val donorId = UUID.randomUUID()
        val donationId = UUID.randomUUID()

        every { legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, LegalDocumentType.CGU) } returns false
        val saved = slot<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.captured }

        service.recordDonorAcceptance(
            donorId, donationId, campaignId, "Marie Curie", "marie@example.org",
            acceptedVersions = mapOf(LegalDocumentType.CGU to "2026-08-26"),
        )

        verify(exactly = 1) { legalAcceptanceRepository.save(any()) }
        assertEquals(LegalAcceptanceSubjectType.DONOR, saved.captured.subjectType)
        assertEquals(LegalDocumentType.CGU, saved.captured.documentType)
        assertEquals("2026-08-26", saved.captured.documentVersion, "records the version the form displayed")
        verify(exactly = 0) { legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, LegalDocumentType.CGV) }
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
    fun `donorAcceptancesForCampaign groups rows by donor, keeping the most-recent-first order`() {
        val donorA = UUID.randomUUID()
        val donorB = UUID.randomUUID()
        val cgvDoc = LegalDocument(documentType = LegalDocumentType.CGV, version = "2026-08-26", content = "text")

        // Most recent first, as the repository method's name promises: donorB's single row, then
        // donorA's two rows (its own most-recent one first).
        val rowsMostRecentFirst = listOf(
            LegalAcceptance(
                subjectType = LegalAcceptanceSubjectType.DONOR, subjectId = donorB,
                documentType = LegalDocumentType.CGU, documentVersion = cguDoc.version,
                signerName = "Ada Lovelace", signerEmail = "ada@example.org", campaignId = campaignId,
            ),
            LegalAcceptance(
                subjectType = LegalAcceptanceSubjectType.DONOR, subjectId = donorA,
                documentType = LegalDocumentType.CGV, documentVersion = cgvDoc.version,
                signerName = "Marie Curie (2)", signerEmail = "marie2@example.org", campaignId = campaignId,
            ),
            LegalAcceptance(
                subjectType = LegalAcceptanceSubjectType.DONOR, subjectId = donorA,
                documentType = LegalDocumentType.CGU, documentVersion = cguDoc.version,
                signerName = "Marie Curie (1)", signerEmail = "marie1@example.org", campaignId = campaignId,
            ),
        )
        every {
            legalAcceptanceRepository.findAllByCampaignIdAndSubjectTypeOrderByAcceptedAtDesc(
                campaignId, LegalAcceptanceSubjectType.DONOR,
            )
        } returns rowsMostRecentFirst

        val groups = service.donorAcceptancesForCampaign(campaignId)

        assertEquals(2, groups.size)
        assertEquals(donorB, groups[0].donorId, "donor with the single most recent row groups first")
        assertEquals("Ada Lovelace", groups[0].donorName)
        assertEquals(1, groups[0].acceptances.size)

        assertEquals(donorA, groups[1].donorId)
        assertEquals("Marie Curie (2)", groups[1].donorName, "group identity comes from that donor's own most recent row")
        assertEquals(2, groups[1].acceptances.size)
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

    @Test
    fun `requireAssociationAcceptance refuses an outdated displayed version and records nothing`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(any(), any(), any(), any())
        } returns false

        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireAssociationAcceptance(
                associationId, LegalDocumentType.CGU, accepted = true, "2026-08-01", "Jean Dupont", "jean@asso.fr", campaignId,
            )
        }
        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireAssociationAcceptance(
                associationId, LegalDocumentType.CGU, accepted = true, null, "Jean Dupont", "jean@asso.fr", campaignId,
            )
        }
        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `acceptForAssociationUser records the current version with the signer snapshot and no campaign`() {
        val userId = UUID.randomUUID()
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(profile(signerName = "Jean Dupont"))
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(any(), any(), any(), any())
        } returns false
        val saved = slot<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.captured }

        val state = service.acceptForAssociationUser(userId, LegalDocumentType.CGU, "2026-08-26")

        assertTrue(state.accepted)
        assertEquals(LegalAcceptanceSubjectType.ASSOCIATION, saved.captured.subjectType)
        assertEquals(associationId, saved.captured.subjectId)
        assertEquals("2026-08-26", saved.captured.documentVersion)
        assertEquals("Jean Dupont", saved.captured.signerName)
        assertEquals("a@b.fr", saved.captured.signerEmail)
        assertNull(saved.captured.campaignId)
    }

    @Test
    fun `acceptForAssociationUser falls back on the association name when no signatory is set`() {
        val userId = UUID.randomUUID()
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(profile(signerName = null))
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(any(), any(), any(), any())
        } returns false
        val saved = slot<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.captured }

        service.acceptForAssociationUser(userId, LegalDocumentType.CGU, "2026-08-26")

        assertEquals("Asso", saved.captured.signerName)
    }

    @Test
    fun `acceptForAssociationUser is a no-op when the current version is already accepted`() {
        val userId = UUID.randomUUID()
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(profile(signerName = null))
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
                LegalAcceptanceSubjectType.ASSOCIATION, associationId, LegalDocumentType.CGU, "2026-08-26",
            )
        } returns true

        // Even a stale version is harmless here: nothing is written, the state just says "accepted".
        val state = service.acceptForAssociationUser(userId, LegalDocumentType.CGU, "2026-08-01")

        assertTrue(state.accepted)
        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `several versions published since the last acceptance cost a single acceptance, of the current one`() {
        // v1 accepted, then v2 and v3 published the same day: only v3 is asked for, v2 can no longer be accepted.
        val userId = UUID.randomUUID()
        val v1 = "2026-11-16-aaaaaaaaaaaa"
        val v3 = "2026-11-16-cccccccccccc"
        val current = LegalDocument(documentType = LegalDocumentType.CGU, version = v3, content = "{}")
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(profile(signerName = null))
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns current
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
                LegalAcceptanceSubjectType.ASSOCIATION, associationId, LegalDocumentType.CGU, any(),
            )
        } answers { arg<String>(3) == v1 }
        val saved = slot<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.captured }

        val before = service.associationAcceptanceStateForUser(userId, LegalDocumentType.CGU)
        assertFalse(before.accepted)
        assertEquals(v3, before.currentVersion, "the gate presents the current version, never an intermediate one")

        assertThrows<LegalDocumentVersionOutdatedException> {
            service.acceptForAssociationUser(userId, LegalDocumentType.CGU, "2026-11-16-bbbbbbbbbbbb")
        }
        service.acceptForAssociationUser(userId, LegalDocumentType.CGU, v3)

        verify(exactly = 1) { legalAcceptanceRepository.save(any()) }
        assertEquals(v3, saved.captured.documentVersion)
    }

    @Test
    fun `requireCurrentDonorVersions passes on the current CGU, without looking at the CGV`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc

        service.requireCurrentDonorVersions(mapOf(LegalDocumentType.CGU to "2026-08-26"))

        verify(exactly = 0) { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGV) }
    }

    @Test
    fun `requireCurrentDonorVersions refuses a stale or missing CGU version`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc

        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireCurrentDonorVersions(mapOf(LegalDocumentType.CGU to "2026-08-01"))
        }
        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireCurrentDonorVersions(emptyMap())
        }
    }

    private fun profile(signerName: String?) = AssociationProfile(
        id = associationId,
        user = User(email = "a@b.fr", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL),
        name = "Asso",
        identifier = "W123456789",
    ).also { it.signerName = signerName }
}

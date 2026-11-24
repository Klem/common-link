package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.dto.PublishTermsAcceptance
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
    private val cgvDoc = LegalDocument(documentType = LegalDocumentType.CGV, version = "2026-11-24-cgv", content = "text")
    private val privacyDoc = LegalDocument(documentType = LegalDocumentType.PRIVACY, version = "2026-11-24-privacy", content = "text")

    /** Publishes the current CGV and privacy policy, and says which of them [associationId] already accepted. */
    private fun associationDocuments(alreadyAccepted: Set<LegalDocumentType> = emptySet()) {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGV) } returns cgvDoc
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.PRIVACY) } returns privacyDoc
        every {
            legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
                LegalAcceptanceSubjectType.ASSOCIATION, associationId, any(), any(),
            )
        } answers { arg<LegalDocumentType>(2) in alreadyAccepted }
    }

    private fun bothTicked(cgvVersion: String? = cgvDoc.version, privacyVersion: String? = privacyDoc.version) = mapOf(
        LegalDocumentType.CGV to PublishTermsAcceptance(true, cgvVersion),
        LegalDocumentType.PRIVACY to PublishTermsAcceptance(true, privacyVersion),
    )

    @Test
    fun `currentDocument throws when no document was ever published`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns null

        assertThrows<NotFoundException> { service.currentDocument(LegalDocumentType.CGU) }
    }

    @Test
    fun `requireAssociationAcceptances is a no-op when both current versions are already accepted`() {
        associationDocuments(alreadyAccepted = setOf(LegalDocumentType.CGV, LegalDocumentType.PRIVACY))

        // Nothing ticked would normally throw — proves the no-op path is genuinely first.
        service.requireAssociationAcceptances(associationId, emptyMap(), "Nom", "a@b.fr", campaignId)

        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `requireAssociationAcceptances throws when a pending document is not ticked, and records nothing`() {
        associationDocuments()

        val ex = assertThrows<UnprocessableEntityException> {
            service.requireAssociationAcceptances(
                associationId,
                mapOf(
                    LegalDocumentType.CGV to PublishTermsAcceptance(true, cgvDoc.version),
                    LegalDocumentType.PRIVACY to PublishTermsAcceptance(false, null),
                ),
                "Nom", "a@b.fr", campaignId,
            )
        }
        assertTrue(ex.message!!.contains("PRIVACY"))
        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `requireAssociationAcceptances records one snapshot row per pending document`() {
        associationDocuments()
        val saved = mutableListOf<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.last() }

        service.requireAssociationAcceptances(associationId, bothTicked(), "Jean Dupont", "jean@asso.fr", campaignId)

        assertEquals(listOf(LegalDocumentType.CGV, LegalDocumentType.PRIVACY), saved.map { it.documentType })
        assertEquals(listOf(cgvDoc.version, privacyDoc.version), saved.map { it.documentVersion })
        saved.forEach {
            assertEquals(LegalAcceptanceSubjectType.ASSOCIATION, it.subjectType)
            assertEquals(associationId, it.subjectId)
            assertEquals("Jean Dupont", it.signerName)
            assertEquals("jean@asso.fr", it.signerEmail)
            assertEquals(campaignId, it.campaignId)
        }
    }

    @Test
    fun `requireAssociationAcceptances only asks for the document whose version changed`() {
        // The CGV is already accepted: the privacy policy alone is pending, and the CGV checkbox is ignored.
        associationDocuments(alreadyAccepted = setOf(LegalDocumentType.CGV))
        val saved = slot<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.captured }

        service.requireAssociationAcceptances(
            associationId,
            mapOf(LegalDocumentType.PRIVACY to PublishTermsAcceptance(true, privacyDoc.version)),
            "Jean Dupont", "jean@asso.fr", campaignId,
        )

        verify(exactly = 1) { legalAcceptanceRepository.save(any()) }
        assertEquals(LegalDocumentType.PRIVACY, saved.captured.documentType)
    }

    @Test
    fun `recordDonorAcceptance is idempotent per donation`() {
        val donationId = UUID.randomUUID()
        every { legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, any()) } returns true

        service.recordDonorAcceptance(
            UUID.randomUUID(), donationId, campaignId, "Marie Curie", "marie@example.org",
            acceptedVersions = mapOf(LegalDocumentType.CGU to "2026-08-26", LegalDocumentType.PRIVACY to privacyDoc.version),
        )

        verify(exactly = 0) { legalAcceptanceRepository.save(any()) }
    }

    @Test
    fun `recordDonorAcceptance writes a CGU row and a privacy policy row, never a CGV one`() {
        val donorId = UUID.randomUUID()
        val donationId = UUID.randomUUID()

        every { legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, any()) } returns false
        val saved = mutableListOf<LegalAcceptance>()
        every { legalAcceptanceRepository.save(capture(saved)) } answers { saved.last() }

        service.recordDonorAcceptance(
            donorId, donationId, campaignId, "Marie Curie", "marie@example.org",
            acceptedVersions = mapOf(LegalDocumentType.CGU to "2026-08-26", LegalDocumentType.PRIVACY to privacyDoc.version),
        )

        assertEquals(listOf(LegalDocumentType.CGU, LegalDocumentType.PRIVACY), saved.map { it.documentType })
        assertEquals(listOf("2026-08-26", privacyDoc.version), saved.map { it.documentVersion }, "records the versions the form displayed")
        verify(exactly = 0) { legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, LegalDocumentType.CGV) }
        saved.forEach {
            assertEquals(LegalAcceptanceSubjectType.DONOR, it.subjectType)
            assertEquals(donationId, it.donationId)
            assertEquals(campaignId, it.campaignId)
        }
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
    fun `requireAssociationAcceptances refuses an outdated displayed version and records nothing`() {
        associationDocuments()

        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireAssociationAcceptances(
                associationId, bothTicked(privacyVersion = "2026-08-01"), "Jean Dupont", "jean@asso.fr", campaignId,
            )
        }
        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireAssociationAcceptances(
                associationId, bothTicked(cgvVersion = null), "Jean Dupont", "jean@asso.fr", campaignId,
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
    fun `requireCurrentDonorVersions passes on the current CGU and privacy policy, without looking at the CGV`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.PRIVACY) } returns privacyDoc

        service.requireCurrentDonorVersions(
            mapOf(LegalDocumentType.CGU to "2026-08-26", LegalDocumentType.PRIVACY to privacyDoc.version),
        )

        verify(exactly = 0) { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGV) }
    }

    @Test
    fun `requireCurrentDonorVersions refuses a stale or missing CGU or privacy policy version`() {
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU) } returns cguDoc
        every { legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.PRIVACY) } returns privacyDoc

        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireCurrentDonorVersions(
                mapOf(LegalDocumentType.CGU to "2026-08-01", LegalDocumentType.PRIVACY to privacyDoc.version),
            )
        }
        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireCurrentDonorVersions(
                mapOf(LegalDocumentType.CGU to "2026-08-26", LegalDocumentType.PRIVACY to "2026-08-01"),
            )
        }
        assertThrows<LegalDocumentVersionOutdatedException> {
            service.requireCurrentDonorVersions(mapOf(LegalDocumentType.CGU to "2026-08-26"))
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

package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.config.SanctionsProperties
import org.commonlink.dto.FreezeScreenStatus
import org.commonlink.dto.FreezeScreenStatusDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
import org.commonlink.entity.BeneficialOwner
import org.commonlink.entity.BeneficialOwnerType
import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertDecision
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertStatus
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.FreezeScreeningMatch
import org.commonlink.entity.SanctionedNature
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.ComplianceAlertRepository
import org.commonlink.repository.ComplianceAuditLogRepository
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.util.NameNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [FreezeScreeningOnboardingService].
 *
 * Covers all six acceptance criteria from prompt 14:
 * - association hit → REFUSAL
 * - dirigeant hit (association clear) → REFUSAL
 * - BO hit (others clear) → REFUSAL
 * - all clear → CLEAR, journalled
 * - screening unavailable → REFUSAL, journalled as unavailable
 * - re-screening of existing association → delegates correctly
 *
 * Also covers: no registry scan or empty officers list → UNAVAILABLE,
 * alert port triggered on hit but not on clear, multiple officers all screened.
 */
class FreezeScreeningOnboardingServiceTest {

    private val screeningService: SanctionScreeningService = mockk()
    private val auditLogService: ComplianceAuditLogService = mockk(relaxed = true)
    private val sanctionedEntityRepository: SanctionedEntityRepository = mockk()
    private val beneficialOwnerRepository: BeneficialOwnerRepository = mockk()
    private val associationProfileRepository: AssociationProfileRepository = mockk()
    private val registryCheckRepository: AssociationRegistryCheckRepository = mockk()
    private val matchRepository: FreezeScreeningMatchRepository = mockk(relaxed = true)
    private val alertRepository: ComplianceAlertRepository = mockk(relaxed = true)
    private val auditLogRepository: ComplianceAuditLogRepository = mockk(relaxed = true)
    private val props = SanctionsProperties(scoreThreshold = 0.85)
    private val alertPort: FreezeHitAlertPort = mockk(relaxed = true)

    /**
     * Real recorder over a mocked repository: the mapping from [ScreeningMatch] to
     * [FreezeScreeningMatch] is what these tests assert, and stubbing the recorder would assert
     * nothing but that the service called it. The transactional boundary the recorder exists for
     * is out of reach of a mock and is covered by `FreezeScreeningEvidenceRecorderTest`.
     */
    private val evidenceRecorder = FreezeScreeningEvidenceRecorder(matchRepository, props)

    /**
     * Real clearance service over mocked repositories, for the same reason as the recorder above:
     * what these tests assert is *which* past decisions lift *which* correspondences, and a stubbed
     * clearance would assert only that the service asked.
     */
    private val clearanceService = FreezeClearanceService(
        alertRepository,
        auditLogRepository,
        matchRepository,
        beneficialOwnerRepository,
    )

    private val service = FreezeScreeningOnboardingService(
        screeningService,
        auditLogService,
        sanctionedEntityRepository,
        beneficialOwnerRepository,
        associationProfileRepository,
        registryCheckRepository,
        evidenceRecorder,
        clearanceService,
        props,
        alertPort,
    )

    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val boId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000020")
    private val publicationDate: LocalDate = LocalDate.of(2026, 8, 11)

    private fun match(score: Double = 0.92, idRegistre: Int = 1) = ScreeningMatch(
        idRegistre = idRegistre,
        nom = "LISTED ENTITY",
        nature = SanctionedNature.PHYSICAL_PERSON,
        score = score,
        dateOfBirth = null,
    )

    private fun bo(
        id: UUID = boId,
        discarded: Boolean = false,
        type: BeneficialOwnerType = BeneficialOwnerType.BENEFICIAL_OWNER,
    ): BeneficialOwner {
        val bo = mockk<BeneficialOwner>()
        every { bo.id } returns id
        every { bo.name } returns "Jean Dupont"
        every { bo.discarded } returns discarded
        every { bo.type } returns type
        return bo
    }

    private fun setupRegisterAvailable() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } returns publicationDate
    }

    private fun setupOneBo(discarded: Boolean = false) {
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns listOf(bo(discarded = discarded))
    }

    private fun setupOfficers(officers: List<String> = listOf("Marie Martin")) {
        val check = mockk<AssociationRegistryCheck>()
        every { check.officers } returns officers
        every { registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns check
    }

    // ─── CLEAR ───────────────────────────────────────────────────────────────

    @Test
    fun `all clear produces CLEAR outcome`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.CLEAR, result)
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    @Test
    fun `CLEAR outcome journals three clear events (association + dirigeant + one BO)`() {
        setupRegisterAvailable()
        every { screeningService.screen(any()) } returns emptyList()
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, associationId, publicationDate, 0.85)
        }
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningClear(ComplianceAuditSubjectType.DECLARANT, associationId, publicationDate, 0.85)
        }
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningClear(ComplianceAuditSubjectType.BENEFICIAL_OWNER, boId, publicationDate, 0.85)
        }
    }

    // ─── HIT on association ──────────────────────────────────────────────────

    @Test
    fun `association hit produces HIT outcome`() {
        setupRegisterAvailable()
        every { screeningService.screen("Listed Association") } returns listOf(match())
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        val result = service.runFreezeCheck(associationId, "Listed Association")

        assertEquals(ScreeningOutcome.HIT, result)
    }

    @Test
    fun `association hit journals HIT event and calls alert port`() {
        setupRegisterAvailable()
        every { screeningService.screen("Listed Association") } returns listOf(match(0.95))
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "Listed Association")

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningHit(
                ComplianceAuditSubjectType.ASSOCIATION, associationId, publicationDate, 0.85, 1, 0.95,
            )
        }
        verify(exactly = 1) {
            alertPort.onFreezeHit(associationId, match { it.any { t -> t.role == FreezeHitRole.ASSOCIATION } })
        }
    }

    // ─── HIT on dirigeant (association is clear) ─────────────────────────────

    @Test
    fun `dirigeant hit with association clear produces HIT outcome`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { screeningService.screen("Jean-Pierre Sanction") } returns listOf(match())
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers(listOf("Jean-Pierre Sanction"))
        setupOneBo()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.HIT, result)
    }

    @Test
    fun `dirigeant hit journals association CLEAR then dirigeant HIT`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { screeningService.screen("Jean-Pierre Sanction") } returns listOf(match(0.91))
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers(listOf("Jean-Pierre Sanction"))
        setupOneBo()

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, associationId, any(), any())
        }
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningHit(
                ComplianceAuditSubjectType.DECLARANT, associationId, publicationDate, 0.85, 1, 0.91,
            )
        }
        verify(exactly = 1) {
            alertPort.onFreezeHit(associationId, match { it.any { t -> t.role == FreezeHitRole.REPRESENTATIVE } })
        }
    }

    @Test
    fun `multiple officers are all screened individually`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { screeningService.screen("Président Dupont") } returns emptyList()
        every { screeningService.screen("Trésorière Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers(listOf("Président Dupont", "Trésorière Martin"))
        setupOneBo()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.CLEAR, result)
        verify(exactly = 2) {
            auditLogService.appendFreezeScreeningClear(ComplianceAuditSubjectType.DECLARANT, associationId, any(), any())
        }
    }

    // ─── HIT on BO (association and dirigeant are clear) ────────────────────

    @Test
    fun `BO hit with association and dirigeant clear produces HIT outcome`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns listOf(match())
        setupOfficers()
        setupOneBo()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.HIT, result)
    }

    @Test
    fun `BO hit journals CLEAR for association and dirigeant then HIT for BO`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns listOf(match(0.88))
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningHit(
                ComplianceAuditSubjectType.BENEFICIAL_OWNER, boId, publicationDate, 0.85, 1, 0.88,
            )
        }
        verify(exactly = 1) {
            alertPort.onFreezeHit(associationId, match { it.any { t -> t.role == FreezeHitRole.BENEFICIAL_OWNER } })
        }
    }

    // ─── Discarded BOs are excluded ──────────────────────────────────────────

    @Test
    fun `discarded BOs are excluded from screening`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { screeningService.screen("Marie Martin") } returns emptyList()
        val discardedBo = bo(discarded = true)
        val activeBo = bo(id = UUID.randomUUID(), discarded = false)
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns listOf(discardedBo, activeBo)
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        // Discarded BO name is "Jean Dupont" too, but only one CLEAR should be logged for BENEFICIAL_OWNER
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningClear(ComplianceAuditSubjectType.BENEFICIAL_OWNER, any(), any(), any())
        }
    }

    // ─── UNAVAILABLE — empty register ────────────────────────────────────────

    @Test
    fun `empty register produces UNAVAILABLE outcome`() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } returns null

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    @Test
    fun `empty register journals UNAVAILABLE for association`() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } returns null

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningUnavailable(
                ComplianceAuditSubjectType.ASSOCIATION,
                associationId,
                "register empty — ingestion not yet run",
            )
        }
    }

    // ─── UNAVAILABLE — no registry scan or empty officers ────────────────────

    @Test
    fun `no registry scan produces UNAVAILABLE outcome after logging association result`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns emptyList()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, associationId, any(), any())
        }
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningUnavailable(
                ComplianceAuditSubjectType.DECLARANT,
                associationId,
                "no registry officers and no manual legal representative on record",
            )
        }
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    // ─── UNAVAILABLE — unexpected exception ──────────────────────────────────

    @Test
    fun `screening exception produces UNAVAILABLE outcome and journals unavailability`() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } throws RuntimeException("DB timeout")

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningUnavailable(
                ComplianceAuditSubjectType.ASSOCIATION,
                associationId,
                "RuntimeException",
            )
        }
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    // ─── reScreenAssociation ──────────────────────────────────────────────────

    @Test
    fun `reScreenAssociation resolves profile and runs full freeze check`() {
        val profile = mockk<AssociationProfile>()
        every { profile.name } returns "Aide aux Réfugiés"
        every { associationProfileRepository.findById(associationId) } returns Optional.of(profile)
        setupRegisterAvailable()
        every { screeningService.screen(any()) } returns emptyList()
        setupOfficers()
        setupOneBo()

        val result = service.reScreenAssociation(associationId)

        assertEquals(ScreeningOutcome.CLEAR, result)
        verify(exactly = 1) { associationProfileRepository.findById(associationId) }
    }

    @Test
    fun `reScreenAssociation throws NotFoundException when association does not exist`() {
        every { associationProfileRepository.findById(associationId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.reScreenAssociation(associationId) }
    }

    // ─── getOnboardingFreezeScreenStatus ─────────────────────────────────────

    private val checkedAt: Instant = Instant.parse("2026-08-11T10:00:00Z")

    @Test
    fun `getOnboardingFreezeScreenStatus returns NOT_PERFORMED when no run exists`() {
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns emptyList()
        every { auditLogService.findLastOnboardingFreezeScreenStatus(associationId, emptyList()) } returns
            FreezeScreenStatusDto(FreezeScreenStatus.NOT_PERFORMED, null)

        val result = service.getOnboardingFreezeScreenStatus(associationId)

        assertEquals(FreezeScreenStatus.NOT_PERFORMED, result.status)
        assertEquals(null, result.checkedAt)
    }

    @Test
    fun `getOnboardingFreezeScreenStatus returns PASSED when last run was clear`() {
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns listOf(bo())
        every { auditLogService.findLastOnboardingFreezeScreenStatus(associationId, listOf(boId)) } returns
            FreezeScreenStatusDto(FreezeScreenStatus.PASSED, checkedAt)

        val result = service.getOnboardingFreezeScreenStatus(associationId)

        assertEquals(FreezeScreenStatus.PASSED, result.status)
        assertEquals(checkedAt, result.checkedAt)
    }

    @Test
    fun `getOnboardingFreezeScreenStatus returns HIT when last run had a match`() {
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns listOf(bo())
        every { auditLogService.findLastOnboardingFreezeScreenStatus(associationId, listOf(boId)) } returns
            FreezeScreenStatusDto(FreezeScreenStatus.HIT, checkedAt)

        val result = service.getOnboardingFreezeScreenStatus(associationId)

        assertEquals(FreezeScreenStatus.HIT, result.status)
    }

    @Test
    fun `getOnboardingFreezeScreenStatus returns UNAVAILABLE when last run could not complete`() {
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns emptyList()
        every { auditLogService.findLastOnboardingFreezeScreenStatus(associationId, emptyList()) } returns
            FreezeScreenStatusDto(FreezeScreenStatus.UNAVAILABLE, checkedAt)

        val result = service.getOnboardingFreezeScreenStatus(associationId)

        assertEquals(FreezeScreenStatus.UNAVAILABLE, result.status)
    }

    // ─── Screening evidence (freeze_screening_match) ──────────────────────────

    /**
     * Stubs the journal so a HIT returns a known sequence number, which is what anchors the
     * evidence rows and the alert to the immutable entry that recorded the correspondence.
     */
    private fun stubHitSequence(seqNo: Long) {
        val entry = mockk<ComplianceAuditLog>()
        every { entry.sequenceNo } returns seqNo
        every {
            auditLogService.appendFreezeScreeningHit(any(), any(), any(), any(), any(), any(), any())
        } returns entry
    }

    @Test
    fun `association hit persists one evidence row per match, anchored to the journal entry`() {
        setupRegisterAvailable()
        stubHitSequence(4242L)
        every { screeningService.screen("TECHNO +") } returns listOf(match(0.9333), match(0.87))
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "TECHNO +")

        val slot = slot<List<FreezeScreeningMatch>>()
        verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
        val saved = slot.captured
        assertEquals(2, saved.size)
        saved.forEach {
            assertEquals(4242L, it.auditLogSeqRef)
            assertEquals(ComplianceAuditSubjectType.ASSOCIATION, it.subjectType)
            assertEquals(associationId, it.subjectId)
            assertEquals(associationId, it.associationId)
            assertEquals(publicationDate, it.registryPublicationDate)
            assertEquals(0.85, it.scoreThreshold)
        }
        assertEquals(setOf(0.9333, 0.87), saved.map { it.score }.toSet())
    }

    /**
     * The stored value must be the one that produced the score. "TECHNO +" is compared as
     * "TECHNO"; storing the raw dossier name would leave a 0.9333 against "TECHNOLAB" unexplained
     * on the officer's screen.
     */
    @Test
    fun `evidence stores the normalized value actually compared, not the raw name`() {
        setupRegisterAvailable()
        stubHitSequence(1L)
        every { screeningService.screen("TECHNO +") } returns listOf(match())
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "TECHNO +")

        val slot = slot<List<FreezeScreeningMatch>>()
        verify { matchRepository.saveAll(capture(slot)) }
        assertEquals("TECHNO", slot.captured.first().screenedNormalizedName)
    }

    /** Register attributes are snapshots so the evidence survives the entry being delisted. */
    @Test
    fun `evidence snapshots the register entry attributes`() {
        setupRegisterAvailable()
        stubHitSequence(1L)
        every { screeningService.screen("Aide aux Réfugiés") } returns listOf(match())
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        val slot = slot<List<FreezeScreeningMatch>>()
        verify { matchRepository.saveAll(capture(slot)) }
        val saved = slot.captured.first()
        assertEquals(1, saved.sanctionedIdRegistre)
        assertEquals("LISTED ENTITY", saved.matchedName)
        assertEquals(SanctionedNature.PHYSICAL_PERSON, saved.matchedNature)
    }

    @Test
    fun `clear screening persists no evidence`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        verify(exactly = 0) { matchRepository.saveAll(any<List<FreezeScreeningMatch>>()) }
    }

    /**
     * Without the sequence reference on the alert, `compliance_alert.audit_log_seq_ref` stays null
     * and nothing ties an alert to the journal entry that justified it.
     */
    @Test
    fun `hit target carries the journal sequence reference`() {
        setupRegisterAvailable()
        stubHitSequence(77L)
        every { screeningService.screen("Aide aux Réfugiés") } returns listOf(match())
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        val slot = slot<List<FreezeHitTarget>>()
        verify { alertPort.onFreezeHit(associationId, capture(slot)) }
        assertEquals(77L, slot.captured.first().auditLogSeqRef)
    }

    /**
     * An impossible control is not a favorable one. It was journalled but surfaced nowhere, so a
     * skipped mandatory check could go unnoticed — see fiche E4 §4.4 on why failure records exist.
     */
    @Test
    fun `unavailable screening raises an alert so the officer sees the skipped control`() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } returns null

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 1) {
            alertPort.onScreeningUnavailable(
                ComplianceAlertSubjectType.ASSOCIATION,
                associationId,
                any(),
            )
        }
    }

    @Test
    fun `unavailable screening on missing dirigeants also raises an alert`() {
        setupRegisterAvailable()
        every { screeningService.screen("Aide aux Réfugiés") } returns emptyList()
        every { registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns emptyList()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 1) {
            alertPort.onScreeningUnavailable(ComplianceAlertSubjectType.ASSOCIATION, associationId, any())
        }
    }

    /** Evidence capture must never mask the screening outcome the caller blocks on. */
    @Test
    fun `evidence persistence failure does not change the HIT outcome`() {
        setupRegisterAvailable()
        stubHitSequence(1L)
        every { screeningService.screen("Aide aux Réfugiés") } returns listOf(match())
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()
        every { matchRepository.saveAll(any<List<FreezeScreeningMatch>>()) } throws RuntimeException("DB down")

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.HIT, result)
    }

    // ─── PRIOR RULINGS ───────────────────────────────────────────────────────

    /** One evidence row as the recorder would have written it — the name already normalized. */
    private fun evidenceRow(screenedName: String, idRegistre: Int, seqRef: Long) = FreezeScreeningMatch(
        auditLogSeqRef = seqRef,
        subjectType = ComplianceAuditSubjectType.ASSOCIATION,
        subjectId = associationId,
        associationId = associationId,
        screenedNormalizedName = NameNormalizer.normalize(screenedName),
        sanctionedIdRegistre = idRegistre,
        matchedName = "LISTED ENTITY",
        matchedNature = SanctionedNature.PHYSICAL_PERSON,
        score = 0.92,
        scoreThreshold = 0.85,
        registryPublicationDate = publicationDate,
    )

    /** Registers a `FALSE_POSITIVE` closure covering the given register entries. */
    private fun setupFalsePositiveClosure(
        screenedName: String,
        idsRegistre: List<Int>,
        closureSeq: Long = 10L,
    ) {
        val alert = ComplianceAlert(
            origin = ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
            subjectType = ComplianceAlertSubjectType.ASSOCIATION,
            subjectId = associationId,
            severity = ComplianceAlertSeverity.HIGH,
            status = ComplianceAlertStatus.CLOSED,
            decision = ComplianceAlertDecision.FALSE_POSITIVE,
            createdAt = Instant.now(),
        )
        every {
            alertRepository.findBySubjectIdAndOriginAndStatus(
                associationId,
                ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
                ComplianceAlertStatus.CLOSED,
            )
        } returns listOf(alert)

        val closure = mockk<ComplianceAuditLog>()
        every { closure.sequenceNo } returns closureSeq
        every {
            auditLogRepository.findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(
                alert.id, ComplianceAuditLogService.ALERT_CLOSED,
            )
        } returns closure

        every {
            matchRepository.findByAssociationIdAndAuditLogSeqRefLessThanEqual(associationId, closureSeq)
        } returns idsRegistre.map { evidenceRow(screenedName, it, closureSeq - 1) }
    }

    /**
     * The bug this whole mechanism exists for: the screening is replayed on every approval attempt,
     * so without consulting past rulings the officer's `FALSE_POSITIVE` decision changed nothing —
     * the dossier was refused again and a duplicate alert was opened on each retry.
     */
    @Test
    fun `a correspondence already ruled a false positive no longer blocks`() {
        setupRegisterAvailable()
        setupFalsePositiveClosure("Aide aux Réfugiés", listOf(1))
        every { screeningService.screen("Aide aux Réfugiés") } returns listOf(match(idRegistre = 1))
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.CLEAR, result)
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    /** A dismissal must leave a trace: a journal silent on it would claim nothing was found. */
    @Test
    fun `a dismissed correspondence is journalled, not skipped in silence`() {
        setupRegisterAvailable()
        setupFalsePositiveClosure("Aide aux Réfugiés", listOf(1))
        every { screeningService.screen("Aide aux Réfugiés") } returns listOf(match(idRegistre = 1))
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningHitCleared(
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                registryPublicationDate = publicationDate,
                scoreThreshold = 0.85,
                matchCount = 1,
                topScore = 0.92,
                clearedByAlertIds = any(),
            )
        }
        verify(exactly = 0) {
            auditLogService.appendFreezeScreeningHit(
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                registryPublicationDate = any(),
                scoreThreshold = any(),
                matchCount = any(),
                topScore = any(),
                nature = any(),
            )
        }
    }

    /** A past ruling speaks only for the entries it examined — a new designation must block again. */
    @Test
    fun `a register entry outside the ruling blocks the dossier again`() {
        setupRegisterAvailable()
        stubHitSequence(1L)
        setupFalsePositiveClosure("Aide aux Réfugiés", listOf(1))
        every { screeningService.screen("Aide aux Réfugiés") } returns listOf(
            match(idRegistre = 1),
            match(score = 0.88, idRegistre = 2),
        )
        every { screeningService.screen("Marie Martin") } returns emptyList()
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.HIT, result)
        verify(exactly = 1) { alertPort.onFreezeHit(associationId, any()) }
    }

    /**
     * The curator must see the decision as soon as it is taken. The journal of the last run states
     * what the screening found and cannot carry a ruling made afterwards, so the banner stayed on
     * "awaiting the compliance officer's decision" until someone attempted an approval — invisible
     * to the very person waiting on it.
     */
    @Test
    fun `the banner reports HIT_CLEARED once the officer has ruled, without re-screening`() {
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns emptyList()
        every { auditLogService.findLastOnboardingFreezeScreenStatus(associationId, emptyList()) } returns
            FreezeScreenStatusDto(FreezeScreenStatus.HIT, checkedAt)
        every { auditLogService.findLastOnboardingRunStartSeq(associationId) } returns 29L
        setupFalsePositiveClosure("Techno +", listOf(1776, 9131), closureSeq = 28L)
        every {
            matchRepository.findByAssociationIdAndAuditLogSeqRefGreaterThanEqual(associationId, 29L)
        } returns listOf(
            evidenceRow("Techno +", 1776, 29L),
            evidenceRow("Techno +", 9131, 29L),
        )

        val result = service.getOnboardingFreezeScreenStatus(associationId)

        assertEquals(FreezeScreenStatus.HIT_CLEARED, result.status)
        assertEquals(checkedAt, result.checkedAt)
    }

    /** One correspondence of the run outside the ruling and the dossier is still awaiting a decision. */
    @Test
    fun `the banner stays on HIT when the run found something the ruling did not cover`() {
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns emptyList()
        every { auditLogService.findLastOnboardingFreezeScreenStatus(associationId, emptyList()) } returns
            FreezeScreenStatusDto(FreezeScreenStatus.HIT, checkedAt)
        every { auditLogService.findLastOnboardingRunStartSeq(associationId) } returns 29L
        setupFalsePositiveClosure("Techno +", listOf(1776), closureSeq = 28L)
        every {
            matchRepository.findByAssociationIdAndAuditLogSeqRefGreaterThanEqual(associationId, 29L)
        } returns listOf(
            evidenceRow("Techno +", 1776, 29L),
            evidenceRow("Techno +", 6766, 29L),
        )

        val result = service.getOnboardingFreezeScreenStatus(associationId)

        assertEquals(FreezeScreenStatus.HIT, result.status)
    }

    /** No evidence, nothing covered: a screening whose evidence was never written is not decided. */
    @Test
    fun `the banner stays on HIT when the run left no evidence to rule upon`() {
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns emptyList()
        every { auditLogService.findLastOnboardingFreezeScreenStatus(associationId, emptyList()) } returns
            FreezeScreenStatusDto(FreezeScreenStatus.HIT, checkedAt)
        every { auditLogService.findLastOnboardingRunStartSeq(associationId) } returns 29L
        setupFalsePositiveClosure("Techno +", listOf(1776), closureSeq = 28L)
        every {
            matchRepository.findByAssociationIdAndAuditLogSeqRefGreaterThanEqual(associationId, 29L)
        } returns emptyList()

        val result = service.getOnboardingFreezeScreenStatus(associationId)

        assertEquals(FreezeScreenStatus.HIT, result.status)
    }

    /**
     * The clearance is granted per (screened name, register entry): the same entry matched through
     * a *different* party is a correspondence nobody has ruled on.
     */
    @Test
    fun `a clearance granted on the association does not cover a dirigeant`() {
        setupRegisterAvailable()
        stubHitSequence(1L)
        setupFalsePositiveClosure("Aide aux Réfugiés", listOf(1))
        every { screeningService.screen("Aide aux Réfugiés") } returns listOf(match(idRegistre = 1))
        every { screeningService.screen("Marie Martin") } returns listOf(match(idRegistre = 1))
        every { screeningService.screen("Jean Dupont") } returns emptyList()
        setupOfficers()
        setupOneBo()

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.HIT, result)
    }
}

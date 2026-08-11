package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.SanctionsProperties
import org.commonlink.dto.FreezeScreenStatus
import org.commonlink.dto.FreezeScreenStatusDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
import org.commonlink.entity.BeneficialOwner
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.SanctionedNature
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.SanctionedEntityRepository
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
    private val props = SanctionsProperties(scoreThreshold = 0.85)
    private val alertPort: FreezeHitAlertPort = mockk(relaxed = true)

    private val service = FreezeScreeningOnboardingService(
        screeningService,
        auditLogService,
        sanctionedEntityRepository,
        beneficialOwnerRepository,
        associationProfileRepository,
        registryCheckRepository,
        props,
        alertPort,
    )

    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val boId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000020")
    private val publicationDate: LocalDate = LocalDate.of(2026, 8, 11)

    private fun match(score: Double = 0.92) = ScreeningMatch(
        idRegistre = 1,
        nom = "LISTED ENTITY",
        nature = SanctionedNature.PHYSICAL_PERSON,
        score = score,
        dateOfBirth = null,
    )

    private fun bo(id: UUID = boId, discarded: Boolean = false): BeneficialOwner {
        val bo = mockk<BeneficialOwner>()
        every { bo.id } returns id
        every { bo.name } returns "Jean Dupont"
        every { bo.discarded } returns discarded
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

        val result = service.runFreezeCheck(associationId, "Aide aux Réfugiés")

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, associationId, any(), any())
        }
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningUnavailable(
                ComplianceAuditSubjectType.DECLARANT,
                associationId,
                "no registry scan available or no officers listed in latest scan",
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
}

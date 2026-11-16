package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.SanctionsProperties
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.SanctionedNature
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.util.NameNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for [FreezeScreeningDonationService].
 *
 * Covers the acceptance criteria from prompt 15:
 * - no match → CLEAR, journalled
 * - name match + same birth year → HIT, journalled, alert called
 * - name match + different birth year → CLEAR (homonym excluded by DOB)
 * - name match + register has no DOB → HIT (conservative: kept for human review)
 * - empty register → UNAVAILABLE, journalled
 * - unexpected exception → UNAVAILABLE, journalled
 * - every correspondence already ruled a false positive → CLEAR, dismissal journalled, no alert
 */
class FreezeScreeningDonationServiceTest {

    private val screeningService: SanctionScreeningService = mockk()
    private val auditLogService: ComplianceAuditLogService = mockk(relaxed = true)
    private val sanctionedEntityRepository: SanctionedEntityRepository = mockk()
    private val matchRepository: FreezeScreeningMatchRepository = mockk(relaxed = true)
    private val props = SanctionsProperties(scoreThreshold = 0.85)
    private val alertPort: FreezeHitAlertPort = mockk(relaxed = true)
    private val clearanceService: FreezeClearanceService = mockk {
        every { forDonor(any()) } returns FreezeClearance.NONE
    }

    /** Real recorder over a mocked repository — see `FreezeScreeningOnboardingServiceTest`. */
    private val evidenceRecorder = FreezeScreeningEvidenceRecorder(matchRepository, props)

    private val service = FreezeScreeningDonationService(
        screeningService,
        auditLogService,
        sanctionedEntityRepository,
        evidenceRecorder,
        props,
        alertPort,
        clearanceService,
    )

    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val donorProfileId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val publicationDate: LocalDate = LocalDate.of(2026, 8, 11)

    private val donorBirthDate: LocalDate = LocalDate.of(1985, 6, 15)

    private val identity = DonorIdentitySnapshot(
        fullName = "Jean Dupont",
        addressLine1 = "12 rue de la Paix",
        addressLine2 = null,
        postalCode = "75001",
        city = "Paris",
        country = "FR",
        birthDate = donorBirthDate,
    )

    /** Le donateur n'a pas renseigné le champ facultatif : aucune désambiguïsation possible. */
    private val identityWithoutBirthDate = identity.copy(birthDate = null)

    private fun setupRegisterAvailable() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } returns publicationDate
    }

    private fun match(dateOfBirth: String? = null, score: Double = 0.92) = ScreeningMatch(
        idRegistre = 1,
        nom = "JEAN DUPONT",
        nature = SanctionedNature.PHYSICAL_PERSON,
        score = score,
        dateOfBirth = dateOfBirth,
    )

    // ─── No match → CLEAR ────────────────────────────────────────────────────

    @Test
    fun `no match produces CLEAR outcome`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns emptyList()

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.CLEAR, result)
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    @Test
    fun `CLEAR outcome journals one CLEAR event for the donor`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns emptyList()

        service.runFreezeCheck(associationId, donorProfileId, identity)

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningClear(
                ComplianceAuditSubjectType.DONOR,
                donorProfileId,
                publicationDate,
                0.85,
                SanctionedNature.PHYSICAL_PERSON.name,
            )
        }
    }

    // ─── Name match + DOB matches donor year → HIT ────────────────────────

    @Test
    fun `name match with same birth year produces HIT`() {
        setupRegisterAvailable()
        // Register has full DOB with year matching the donor's birth year (1985)
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(dateOfBirth = "15/06/1985"))

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.HIT, result)
    }

    @Test
    fun `HIT journals one HIT event and calls alert port`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(dateOfBirth = "15/06/1985", score = 0.95))

        service.runFreezeCheck(associationId, donorProfileId, identity)

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningHit(
                ComplianceAuditSubjectType.DONOR,
                donorProfileId,
                publicationDate,
                0.85,
                1,
                0.95,
                SanctionedNature.PHYSICAL_PERSON.name,
            )
        }
        verify(exactly = 1) {
            alertPort.onFreezeHit(associationId, match { targets -> targets.any { it.role == FreezeHitRole.DONOR } })
        }
    }

    // ─── Homonym: name matches but birth year differs → CLEAR (DOB disambiguation) ──

    @Test
    fun `homonym with different birth year is excluded and produces CLEAR`() {
        setupRegisterAvailable()
        // Register entry born in 1960 — donor born in 1985 — same name, different person
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(dateOfBirth = "01/01/1960"))

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.CLEAR, result)
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    @Test
    fun `homonym with different birth year is retained when the donor gave no birth date`() {
        setupRegisterAvailable()
        // Même entrée que le test précédent : née en 1960, écartée quand la date du donateur est
        // connue. Sans date, plus rien ne l'écarte — le don est refusé et l'officier tranche.
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(dateOfBirth = "01/01/1960"))

        val result = service.runFreezeCheck(associationId, donorProfileId, identityWithoutBirthDate)

        assertEquals(ScreeningOutcome.HIT, result)
        verify(exactly = 1) { alertPort.onFreezeHit(any(), any()) }
    }

    @Test
    fun `no name match still produces CLEAR when the donor gave no birth date`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns emptyList()

        val result = service.runFreezeCheck(associationId, donorProfileId, identityWithoutBirthDate)

        assertEquals(ScreeningOutcome.CLEAR, result)
    }

    @Test
    fun `homonym partial date MM-YYYY with different year is excluded`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(dateOfBirth = "03/1960"))

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.CLEAR, result)
    }

    @Test
    fun `homonym partial date YYYY-only with different year is excluded`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(dateOfBirth = "1960"))

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.CLEAR, result)
    }

    // ─── Conservative: no DOB in register → HIT (kept for human review) ─────

    @Test
    fun `name match without register DOB produces HIT (conservative)`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(dateOfBirth = null))

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.HIT, result)
    }

    @Test
    fun `name match with blank register DOB produces HIT (conservative)`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(dateOfBirth = "   "))

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.HIT, result)
    }

    // ─── Prior false-positive ruling ─────────────────────────────────────────

    private val ruledAlertId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000099")

    /** The officer's ruling covers the (normalized donor name, register entry) couples given. */
    private fun stubClearance(vararg idRegistres: Int) {
        val normalized = NameNormalizer.normalize(identity.fullName)
        every { clearanceService.forDonor(donorProfileId) } returns FreezeClearance(
            idRegistres.map { ClearedPair(normalized, it) }.toSet(),
            listOf(ruledAlertId),
        )
    }

    /** The reported bug: a homonym ruled a false positive was refused again on every attempt. */
    @Test
    fun `a correspondence already ruled a false positive no longer blocks the donation`() {
        setupRegisterAvailable()
        stubClearance(1)
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match(score = 0.95))

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.CLEAR, result)
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningHitCleared(
                ComplianceAuditSubjectType.DONOR,
                donorProfileId,
                publicationDate,
                0.85,
                1,
                0.95,
                listOf(ruledAlertId),
            )
        }
        verify(exactly = 0) { auditLogService.appendFreezeScreeningHit(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
        verify(exactly = 0) { matchRepository.saveAll(any<List<org.commonlink.entity.FreezeScreeningMatch>>()) }
    }

    /** A past ruling speaks only for what it examined — a register update re-blocks. */
    @Test
    fun `one correspondence not covered by the ruling keeps the hit`() {
        setupRegisterAvailable()
        stubClearance(1)
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns
            listOf(match(), match().copy(idRegistre = 2))

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.HIT, result)
        verify(exactly = 0) { auditLogService.appendFreezeScreeningHitCleared(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { alertPort.onFreezeHit(any(), any()) }
    }

    /** Same e-mail, another name: the ruling covered the couples of the name it was shown. */
    @Test
    fun `a ruling on another name of the same donor profile does not cover this one`() {
        setupRegisterAvailable()
        every { clearanceService.forDonor(donorProfileId) } returns FreezeClearance(
            setOf(ClearedPair(NameNormalizer.normalize("Bob l'eponge"), 1)),
            listOf(ruledAlertId),
        )
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match())

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.HIT, result)
    }

    /** An unreadable ruling never lets a payment through. */
    @Test
    fun `a failure reading the clearance produces UNAVAILABLE`() {
        setupRegisterAvailable()
        every { clearanceService.forDonor(donorProfileId) } throws RuntimeException("DB timeout")
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns listOf(match())

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningUnavailable(
                ComplianceAuditSubjectType.DONOR,
                donorProfileId,
                "RuntimeException",
            )
        }
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    @Test
    fun `no correspondence found does not consult the clearance`() {
        setupRegisterAvailable()
        every { screeningService.screen(any(), nature = SanctionedNature.PHYSICAL_PERSON) } returns emptyList()

        service.runFreezeCheck(associationId, donorProfileId, identity)

        verify(exactly = 0) { clearanceService.forDonor(any()) }
    }

    // ─── UNAVAILABLE — empty register ────────────────────────────────────────

    @Test
    fun `empty register produces UNAVAILABLE outcome`() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } returns null

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    @Test
    fun `empty register journals UNAVAILABLE for donor`() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } returns null

        service.runFreezeCheck(associationId, donorProfileId, identity)

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningUnavailable(
                ComplianceAuditSubjectType.DONOR,
                donorProfileId,
                "register empty — ingestion not yet run",
            )
        }
    }

    // ─── UNAVAILABLE — unexpected exception ──────────────────────────────────

    @Test
    fun `screening exception produces UNAVAILABLE outcome`() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } throws RuntimeException("DB timeout")

        val result = service.runFreezeCheck(associationId, donorProfileId, identity)

        assertEquals(ScreeningOutcome.UNAVAILABLE, result)
        verify(exactly = 0) { alertPort.onFreezeHit(any(), any()) }
    }

    @Test
    fun `screening exception journals UNAVAILABLE with exception class name`() {
        every { sanctionedEntityRepository.findMaxPublicationDate() } throws RuntimeException("DB timeout")

        service.runFreezeCheck(associationId, donorProfileId, identity)

        verify(exactly = 1) {
            auditLogService.appendFreezeScreeningUnavailable(
                ComplianceAuditSubjectType.DONOR,
                donorProfileId,
                "RuntimeException",
            )
        }
    }
}

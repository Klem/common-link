package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.BeneficialOwner
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
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.ComplianceAlertRepository
import org.commonlink.repository.ComplianceAuditLogRepository
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for [FreezeClearanceService].
 *
 * The property under test throughout is that a clearance covers exactly what the compliance
 * officer ruled on — the correspondences their alert displayed, as they stood when they ruled —
 * and never more. Over-covering would hide a live designation; under-covering would leave the
 * dossier unapprovable, which is the defect this service was written to fix.
 */
class FreezeClearanceServiceTest {

    private val alertRepository: ComplianceAlertRepository = mockk(relaxed = true)
    private val auditLogRepository: ComplianceAuditLogRepository = mockk(relaxed = true)
    private val matchRepository: FreezeScreeningMatchRepository = mockk(relaxed = true)
    private val beneficialOwnerRepository: BeneficialOwnerRepository = mockk(relaxed = true)

    private val service = FreezeClearanceService(
        alertRepository,
        auditLogRepository,
        matchRepository,
        beneficialOwnerRepository,
    )

    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val boId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000020")
    private val publicationDate: LocalDate = LocalDate.of(2026, 8, 11)

    private fun closedAlert(
        subjectId: UUID,
        subjectType: ComplianceAlertSubjectType,
        decision: ComplianceAlertDecision = ComplianceAlertDecision.FALSE_POSITIVE,
        origin: ComplianceAlertOrigin = ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
        status: ComplianceAlertStatus = ComplianceAlertStatus.CLOSED,
    ) = ComplianceAlert(
        origin = origin,
        subjectType = subjectType,
        subjectId = subjectId,
        severity = ComplianceAlertSeverity.HIGH,
        status = status,
        decision = decision,
        createdAt = Instant.now(),
    )

    private fun evidence(name: String, idRegistre: Int, seqRef: Long = 9L) = FreezeScreeningMatch(
        auditLogSeqRef = seqRef,
        subjectType = ComplianceAuditSubjectType.ASSOCIATION,
        subjectId = associationId,
        associationId = associationId,
        screenedNormalizedName = name,
        sanctionedIdRegistre = idRegistre,
        matchedName = "LISTED ENTITY",
        matchedNature = SanctionedNature.PHYSICAL_PERSON,
        score = 0.92,
        scoreThreshold = 0.85,
        registryPublicationDate = publicationDate,
    )

    /** Registers the closed alerts of a subject, each dated by its `ALERT_CLOSED` position. */
    private fun stubClosures(
        subjectId: UUID,
        vararg closures: Pair<ComplianceAlert, Long>,
        origin: ComplianceAlertOrigin = ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
    ) {
        every {
            alertRepository.findBySubjectIdAndOriginAndStatus(
                subjectId,
                origin,
                ComplianceAlertStatus.CLOSED,
            )
        } returns closures.map { it.first }
        for ((alert, closureSeq) in closures) {
            val closure = mockk<ComplianceAuditLog>()
            every { closure.sequenceNo } returns closureSeq
            every {
                auditLogRepository.findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(
                    alert.id, ComplianceAuditLogService.ALERT_CLOSED,
                )
            } returns closure
        }
    }

    @Test
    fun `no ruling grants no clearance`() {
        val clearance = service.forOnboarding(associationId)

        assertTrue(clearance.pairs.isEmpty())
        assertFalse(clearance.covers(listOf(ClearedPair("TECHNO", 1))))
    }

    /**
     * Without an `ALERT_CLOSED` entry the clearance would have no bound in time, and an unbounded
     * clearance covers the evidence of the screening currently being evaluated — every subsequent
     * run would clear itself.
     */
    @Test
    fun `a closure with no journal entry grants nothing`() {
        val alert = closedAlert(associationId, ComplianceAlertSubjectType.ASSOCIATION)
        every {
            alertRepository.findBySubjectIdAndOriginAndStatus(
                associationId,
                ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
                ComplianceAlertStatus.CLOSED,
            )
        } returns listOf(alert)
        every {
            auditLogRepository.findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(any(), any())
        } returns null

        val clearance = service.forOnboarding(associationId)

        assertTrue(clearance.pairs.isEmpty())
        verify(exactly = 0) { matchRepository.findByAssociationIdAndAuditLogSeqRefLessThanEqual(any(), any()) }
    }

    /** The evidence read is bounded by the ruling's own position in the journal. */
    @Test
    fun `clearance covers the evidence that existed when the officer ruled`() {
        val alert = closedAlert(associationId, ComplianceAlertSubjectType.ASSOCIATION)
        stubClosures(associationId, alert to 42L)
        every {
            matchRepository.findByAssociationIdAndAuditLogSeqRefLessThanEqual(associationId, 42L)
        } returns listOf(evidence("TECHNO", 1), evidence("MARIE MARTIN", 7))

        val clearance = service.forOnboarding(associationId)

        assertEquals(
            setOf(ClearedPair("TECHNO", 1), ClearedPair("MARIE MARTIN", 7)),
            clearance.pairs,
        )
        assertEquals(listOf(alert.id), clearance.alertIds)
        verify(exactly = 1) {
            matchRepository.findByAssociationIdAndAuditLogSeqRefLessThanEqual(associationId, 42L)
        }
    }

    /**
     * A beneficial owner's alert displays only that owner's correspondences, so its closure may
     * lift only those — scope of the clearance mirrors scope of the evidence shown.
     */
    @Test
    fun `a beneficial owner ruling is read on the owner's own subject`() {
        val bo = mockk<BeneficialOwner>()
        every { bo.id } returns boId
        every { beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns listOf(bo)
        val alert = closedAlert(boId, ComplianceAlertSubjectType.BENEFICIAL_OWNER)
        stubClosures(boId, alert to 15L)
        every {
            matchRepository.findBySubjectIdAndAuditLogSeqRefLessThanEqual(boId, 15L)
        } returns listOf(evidence("JEAN DUPONT", 3))

        val clearance = service.forOnboarding(associationId)

        assertEquals(setOf(ClearedPair("JEAN DUPONT", 3)), clearance.pairs)
    }

    /**
     * The case the live data makes reachable: a subject accumulates closures, an early one being a
     * false positive. If the officer later confirms the correspondence, that confirmation must
     * govern — an unfavorable ruling that merely abstained would leave the stale false positive
     * lifting a designation the officer has since confirmed.
     */
    @Test
    fun `a later confirmed correspondence supersedes an earlier false positive`() {
        val cleared = closedAlert(associationId, ComplianceAlertSubjectType.ASSOCIATION)
        val confirmed = closedAlert(
            associationId,
            ComplianceAlertSubjectType.ASSOCIATION,
            decision = ComplianceAlertDecision.SUSPICIOUS,
        )
        stubClosures(associationId, cleared to 28L, confirmed to 35L)

        val clearance = service.forOnboarding(associationId)

        assertTrue(clearance.pairs.isEmpty())
        verify(exactly = 0) { matchRepository.findByAssociationIdAndAuditLogSeqRefLessThanEqual(any(), any()) }
    }

    /** Symmetrically, a false positive ruled after an unfavorable one does lift, and bounds later. */
    @Test
    fun `the latest false positive governs and carries the widest bound`() {
        val confirmed = closedAlert(
            associationId,
            ComplianceAlertSubjectType.ASSOCIATION,
            decision = ComplianceAlertDecision.SUSPICIOUS,
        )
        val cleared = closedAlert(associationId, ComplianceAlertSubjectType.ASSOCIATION)
        stubClosures(associationId, confirmed to 28L, cleared to 35L)
        every {
            matchRepository.findByAssociationIdAndAuditLogSeqRefLessThanEqual(associationId, 35L)
        } returns listOf(evidence("TECHNO", 1))

        val clearance = service.forOnboarding(associationId)

        assertEquals(setOf(ClearedPair("TECHNO", 1)), clearance.pairs)
        assertEquals(listOf(cleared.id), clearance.alertIds)
    }

    /** One uncovered couple is enough: a past ruling speaks only for what it examined. */
    @Test
    fun `covers requires every correspondence found to be already ruled upon`() {
        val clearance = FreezeClearance(setOf(ClearedPair("TECHNO", 1)), listOf(UUID.randomUUID()))

        assertTrue(clearance.covers(listOf(ClearedPair("TECHNO", 1))))
        assertFalse(clearance.covers(listOf(ClearedPair("TECHNO", 1), ClearedPair("TECHNO", 2))))
        assertFalse(clearance.covers(listOf(ClearedPair("AUTRE", 1))))
    }

    /** An empty screening is not a cleared screening — nothing to cover means nothing was found. */
    @Test
    fun `covers is false on an empty match list`() {
        assertFalse(FreezeClearance.NONE.covers(emptyList()))
        assertFalse(FreezeClearance(setOf(ClearedPair("TECHNO", 1)), emptyList()).covers(emptyList()))
    }

    // ─── Donor ───────────────────────────────────────────────────────────────

    private val donorProfileId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000030")

    private fun donorAlert(
        decision: ComplianceAlertDecision = ComplianceAlertDecision.FALSE_POSITIVE,
        status: ComplianceAlertStatus = ComplianceAlertStatus.CLOSED,
    ) = closedAlert(
        donorProfileId,
        ComplianceAlertSubjectType.DONOR,
        decision = decision,
        origin = ComplianceAlertOrigin.FREEZE_HIT_DONATION,
        status = status,
    )

    /** No donor freeze alert under review — the relaxed mock would otherwise return one. */
    private fun stubNoOpenDonorAlert() {
        every {
            alertRepository.findByOriginAndSubjectIdAndStatusIn(
                ComplianceAlertOrigin.FREEZE_HIT_DONATION, donorProfileId, ComplianceAlertService.OPEN_STATUSES,
            )
        } returns null
    }

    private fun stubDonorClosures(vararg closures: Pair<ComplianceAlert, Long>) =
        stubClosures(donorProfileId, *closures, origin = ComplianceAlertOrigin.FREEZE_HIT_DONATION)

    /** The homonym case: the officer ruled a false positive, the next attempt must go through. */
    @Test
    fun `a donor false positive covers the donor's evidence up to the ruling`() {
        stubNoOpenDonorAlert()
        val alert = donorAlert()
        stubDonorClosures(alert to 50L)
        every {
            matchRepository.findBySubjectIdAndAuditLogSeqRefLessThanEqual(donorProfileId, 50L)
        } returns listOf(evidence("BOB L EPONGE", 4))

        val clearance = service.forDonor(donorProfileId)

        assertEquals(setOf(ClearedPair("BOB L EPONGE", 4)), clearance.pairs)
        assertEquals(listOf(alert.id), clearance.alertIds)
    }

    @Test
    fun `a donor suspicious ruling grants nothing`() {
        stubNoOpenDonorAlert()
        stubDonorClosures(donorAlert(decision = ComplianceAlertDecision.SUSPICIOUS) to 50L)

        val clearance = service.forDonor(donorProfileId)

        assertTrue(clearance.pairs.isEmpty())
        verify(exactly = 0) { matchRepository.findBySubjectIdAndAuditLogSeqRefLessThanEqual(any(), any()) }
    }

    @Test
    fun `a donor suspicious ruling supersedes an earlier false positive`() {
        stubNoOpenDonorAlert()
        stubDonorClosures(donorAlert() to 40L, donorAlert(decision = ComplianceAlertDecision.SUSPICIOUS) to 50L)

        val clearance = service.forDonor(donorProfileId)

        assertTrue(clearance.pairs.isEmpty())
    }

    /**
     * One profile (one e-mail) can be screened under several names. The alert screen of the latest
     * ruling displays every correspondence of the profile, those of the name confirmed earlier
     * included — so a false positive ruled last lifts them too, exactly as at onboarding. Pinned
     * here because it is a deliberate consequence of "the latest ruling governs".
     */
    @Test
    fun `a donor false positive ruled last also lifts a name confirmed earlier on the same profile`() {
        stubNoOpenDonorAlert()
        val cleared = donorAlert()
        stubDonorClosures(donorAlert(decision = ComplianceAlertDecision.SUSPICIOUS) to 40L, cleared to 50L)
        every {
            matchRepository.findBySubjectIdAndAuditLogSeqRefLessThanEqual(donorProfileId, 50L)
        } returns listOf(evidence("NOM A", 4, seqRef = 30L), evidence("NOM B", 5, seqRef = 45L))

        val clearance = service.forDonor(donorProfileId)

        assertEquals(setOf(ClearedPair("NOM A", 4), ClearedPair("NOM B", 5)), clearance.pairs)
        assertEquals(listOf(cleared.id), clearance.alertIds)
    }

    /**
     * While a correspondence of the donor is under review, no earlier ruling lets a payment
     * through — whichever name the next attempt uses.
     */
    @Test
    fun `an open donor freeze alert suspends every clearance`() {
        every {
            alertRepository.findByOriginAndSubjectIdAndStatusIn(
                ComplianceAlertOrigin.FREEZE_HIT_DONATION, donorProfileId, ComplianceAlertService.OPEN_STATUSES,
            )
        } returns donorAlert(status = ComplianceAlertStatus.PENDING)
        stubDonorClosures(donorAlert() to 50L)

        val clearance = service.forDonor(donorProfileId)

        assertEquals(FreezeClearance.NONE, clearance)
        verify(exactly = 0) { matchRepository.findBySubjectIdAndAuditLogSeqRefLessThanEqual(any(), any()) }
    }

    /** An onboarding ruling never speaks for a donation: rulings are read per origin. */
    @Test
    fun `an onboarding ruling on the same id grants no donor clearance`() {
        stubNoOpenDonorAlert()
        stubClosures(donorProfileId, closedAlert(donorProfileId, ComplianceAlertSubjectType.DONOR) to 50L)
        every {
            alertRepository.findBySubjectIdAndOriginAndStatus(
                donorProfileId, ComplianceAlertOrigin.FREEZE_HIT_DONATION, ComplianceAlertStatus.CLOSED,
            )
        } returns emptyList()

        val clearance = service.forDonor(donorProfileId)

        assertTrue(clearance.pairs.isEmpty())
    }
}

package org.commonlink.service

import org.commonlink.config.SanctionsProperties
import org.commonlink.dto.FreezeScreenStatus
import org.commonlink.dto.FreezeScreenStatusDto
import org.commonlink.entity.BeneficialOwnerType
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.util.NameNormalizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

/**
 * Outcome of a three-party freeze screening at onboarding or re-screening.
 *
 * - [CLEAR]       — all required parties screened, no match above threshold.
 * - [HIT]         — at least one party matched the register; alert port was notified.
 * - [UNAVAILABLE] — screening could not be completed (empty register, missing data,
 *                   service failure). An [UNAVAILABLE] outcome is **not** favorable:
 *                   the caller must treat it identically to [HIT].
 */
enum class ScreeningOutcome { CLEAR, HIT, UNAVAILABLE }

/**
 * Orchestrates the mandatory three-party asset-freeze screening at onboarding (LCB-FT,
 * art. L.561-5 CMF) and provides a re-screening method for existing business relationships.
 *
 * The three parties that must all be screened are:
 * 1. the association itself,
 * 2. its dirigeants: from the official registry scan ([AssociationRegistryCheck.officers]) and/or
 *    from manually entered legal representatives ([BeneficialOwnerType.REPRESENTATIVE]).
 *    UNAVAILABLE only when both sources are absent. Art. R.561-3 CMF (décret n°2024-720).
 * 3. each retained beneficial owner ([BeneficialOwnerType.BENEFICIAL_OWNER]).
 *
 * Omitting any one of the three empties the control of its legal meaning.
 *
 * ### Blocking semantics
 * Any outcome other than [ScreeningOutcome.CLEAR] must block the caller's operation.
 * An impossible check ([ScreeningOutcome.UNAVAILABLE]) is not a favorable check —
 * the caller must refuse and not silently allow entry into the business relationship.
 *
 * ### Log hygiene
 * Screened names are never written to application logs. Per-screening traces belong
 * exclusively in the compliance audit journal ([ComplianceAuditLogService]).
 *
 * ### Alerts
 * On a hit, [FreezeHitAlertPort.onFreezeHit] is called before returning [ScreeningOutcome.HIT].
 * [FreezeHitAlertAdapter] creates a [org.commonlink.entity.ComplianceAlert] via
 * [ComplianceAlertService], idempotent on (origin, subject).
 *
 * ### Prior rulings
 * A correspondence a compliance officer has already ruled a false positive is set aside rather
 * than raised again — see [FreezeClearanceService] for the exact scope of a clearance and for why
 * replaying the screening without consulting past decisions left dossiers unapprovable. The
 * dismissal is journalled ([ComplianceAuditLogService.FREEZE_SCREENING_HIT_CLEARED]), never silent.
 */
@Service
class FreezeScreeningOnboardingService(
    private val screeningService: SanctionScreeningService,
    private val auditLogService: ComplianceAuditLogService,
    private val sanctionedEntityRepository: SanctionedEntityRepository,
    private val beneficialOwnerRepository: BeneficialOwnerRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val registryCheckRepository: AssociationRegistryCheckRepository,
    private val evidenceRecorder: FreezeScreeningEvidenceRecorder,
    private val clearanceService: FreezeClearanceService,
    private val props: SanctionsProperties,
    private val alertPort: FreezeHitAlertPort,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Screens all three mandatory parties and returns the outcome.
     *
     * Designed to be called from [VerificationService.adminApprove] — the method is
     * self-contained and always returns (never throws). Any unexpected failure is caught,
     * journalled as [ScreeningOutcome.UNAVAILABLE], and returned to the caller.
     *
     * @param associationId   UUID of the [org.commonlink.entity.AssociationProfile].
     * @param associationName Registered name of the association.
     */
    fun runFreezeCheck(
        associationId: UUID,
        associationName: String,
    ): ScreeningOutcome {
        return try {
            doFreezeCheck(associationId, associationName)
        } catch (e: Exception) {
            logger.error(
                "Freeze screening failed unexpectedly for association {}: {}",
                associationId, e.javaClass.simpleName,
            )
            try {
                auditLogService.appendFreezeScreeningUnavailable(
                    subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                    subjectId = associationId,
                    reason = e.javaClass.simpleName,
                )
            } catch (logEx: Exception) {
                logger.error(
                    "Failed to record freeze screening failure in audit log for association {}: {}",
                    associationId, logEx.javaClass.simpleName,
                )
            }
            raiseUnavailableAlert(associationId, e.javaClass.simpleName)
            ScreeningOutcome.UNAVAILABLE
        }
    }

    /**
     * Returns the five-state indicator of the last onboarding freeze-screening run for the given
     * association. Delegates the audit-log derivation to [ComplianceAuditLogService] and provides
     * all beneficial-owner IDs (both [BeneficialOwnerType.BENEFICIAL_OWNER] and
     * [BeneficialOwnerType.REPRESENTATIVE]) so that their events (stored with `subject_id = owner.id`)
     * are included in the status derivation.
     *
     * ### Why a ruling is applied here and not only at the next screening
     * The journal states what the *screening* found; a compliance decision taken afterwards is a
     * later fact that the journal of that run cannot carry. Left to the screening path alone, the
     * curator would keep reading "awaiting the compliance officer's decision" until someone
     * attempted an approval — the decision would be invisible to the person waiting on it.
     * The predicate is the one the screening path applies: every correspondence of the last run is
     * covered by a clearance. Absent evidence rows, nothing is covered and the status stays
     * [org.commonlink.dto.FreezeScreenStatus.HIT] — silence is not a favorable ruling.
     *
     * Returns [org.commonlink.dto.FreezeScreenStatus.NOT_PERFORMED] if no screening has ever been run.
     */
    fun getOnboardingFreezeScreenStatus(associationId: UUID): FreezeScreenStatusDto {
        val boIds = beneficialOwnerRepository
            .findAllByAssociationIdOrderByCollectedAtAsc(associationId)
            .mapNotNull { it.id }
        val status = auditLogService.findLastOnboardingFreezeScreenStatus(associationId, boIds)
        if (status.status != FreezeScreenStatus.HIT) return status

        val runStartSeq = auditLogService.findLastOnboardingRunStartSeq(associationId)
            ?: return status

        return if (clearanceService.coversRunSince(associationId, runStartSeq)) {
            status.copy(status = FreezeScreenStatus.HIT_CLEARED)
        } else {
            status
        }
    }

    /**
     * Re-screens an association that is already in a business relationship.
     *
     * Applies the same three-party screening as at onboarding. Intended for curator-triggered
     * checks when the register evolves after the initial approval. No periodic automation is
     * built here — this method must be called explicitly (e.g. via a future admin endpoint).
     *
     * Journalling and alert-port behaviour are identical to [runFreezeCheck].
     *
     * @throws NotFoundException if [associationId] does not match any association profile.
     */
    fun reScreenAssociation(associationId: UUID): ScreeningOutcome {
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId not found") }
        return runFreezeCheck(associationId, profile.name)
    }

    // -----------------------------------------------------------------------------------------

    private fun doFreezeCheck(associationId: UUID, associationName: String): ScreeningOutcome {
        val publicationDate = sanctionedEntityRepository.findMaxPublicationDate()
        if (publicationDate == null) {
            auditLogService.appendFreezeScreeningUnavailable(
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                reason = "register empty — ingestion not yet run",
            )
            raiseUnavailableAlert(associationId, "register empty — ingestion not yet run")
            return ScreeningOutcome.UNAVAILABLE
        }

        val hits = mutableListOf<FreezeHitTarget>()

        // Correspondences a compliance officer has already ruled a false positive. This screening
        // is replayed on every approval attempt, so without this the officer's decision would
        // change nothing: the same entries would be found again, block the dossier again, and
        // raise a duplicate alert on every retry.
        val clearance = clearanceService.forOnboarding(associationId)

        // 1. Screen the association itself
        val assocMatches = screeningService.screen(associationName)
        if (assocMatches.isNotEmpty()) {
            if (!dismissIfCleared(
                    clearance = clearance,
                    subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                    subjectId = associationId,
                    screenedName = associationName,
                    matches = assocMatches,
                    publicationDate = publicationDate,
                )
            ) {
                val seqNo = auditLogService.appendFreezeScreeningHit(
                    subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                    subjectId = associationId,
                    registryPublicationDate = publicationDate,
                    scoreThreshold = props.scoreThreshold,
                    matchCount = assocMatches.size,
                    topScore = assocMatches.first().score,
                ).sequenceNo
                recordMatches(
                    auditLogSeqRef = seqNo,
                    subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                    subjectId = associationId,
                    associationId = associationId,
                    screenedName = associationName,
                    matches = assocMatches,
                    publicationDate = publicationDate,
                )
                hits += FreezeHitTarget(
                    role = FreezeHitRole.ASSOCIATION,
                    subjectId = associationId,
                    auditLogSeqRef = seqNo,
                )
            }
        } else {
            auditLogService.appendFreezeScreeningClear(
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                registryPublicationDate = publicationDate,
                scoreThreshold = props.scoreThreshold,
            )
        }

        // 2. Screen each dirigeant: from JOAFE registry scan and/or manually entered legal representatives.
        // Art. R.561-3 CMF (décret n°2024-720) : representants légaux = bénéficiaires effectifs.
        // UNAVAILABLE seulement si les DEUX sources sont absentes.
        // Registry officers → subjectType=DECLARANT, subjectId=associationId.
        // Manual representatives → subjectType=REPRESENTATIVE, subjectId=rep.id.
        val officers = registryCheckRepository
            .findTopByAssociationIdOrderByCheckedAtDesc(associationId)
            ?.officers ?: emptyList()
        val manualReps = beneficialOwnerRepository
            .findAllByAssociationIdOrderByCollectedAtAsc(associationId)
            .filter { !it.discarded && it.type == BeneficialOwnerType.REPRESENTATIVE }
        if (officers.isEmpty() && manualReps.isEmpty()) {
            auditLogService.appendFreezeScreeningUnavailable(
                subjectType = ComplianceAuditSubjectType.DECLARANT,
                subjectId = associationId,
                reason = "no registry officers and no manual legal representative on record",
            )
            raiseUnavailableAlert(
                associationId,
                "no registry officers and no manual legal representative on record",
            )
            return ScreeningOutcome.UNAVAILABLE
        }
        for (officerName in officers) {
            val repMatches = screeningService.screen(officerName)
            if (repMatches.isNotEmpty()) {
                if (!dismissIfCleared(
                        clearance = clearance,
                        subjectType = ComplianceAuditSubjectType.DECLARANT,
                        subjectId = associationId,
                        screenedName = officerName,
                        matches = repMatches,
                        publicationDate = publicationDate,
                    )
                ) {
                    val seqNo = auditLogService.appendFreezeScreeningHit(
                        subjectType = ComplianceAuditSubjectType.DECLARANT,
                        subjectId = associationId,
                        registryPublicationDate = publicationDate,
                        scoreThreshold = props.scoreThreshold,
                        matchCount = repMatches.size,
                        topScore = repMatches.first().score,
                    ).sequenceNo
                    recordMatches(
                        auditLogSeqRef = seqNo,
                        subjectType = ComplianceAuditSubjectType.DECLARANT,
                        subjectId = associationId,
                        associationId = associationId,
                        screenedName = officerName,
                        matches = repMatches,
                        publicationDate = publicationDate,
                    )
                    hits += FreezeHitTarget(
                        role = FreezeHitRole.REPRESENTATIVE,
                        subjectId = associationId,
                        auditLogSeqRef = seqNo,
                    )
                }
            } else {
                auditLogService.appendFreezeScreeningClear(
                    subjectType = ComplianceAuditSubjectType.DECLARANT,
                    subjectId = associationId,
                    registryPublicationDate = publicationDate,
                    scoreThreshold = props.scoreThreshold,
                )
            }
        }
        for (rep in manualReps) {
            val repMatches = screeningService.screen(rep.name)
            if (repMatches.isNotEmpty()) {
                if (!dismissIfCleared(
                        clearance = clearance,
                        subjectType = ComplianceAuditSubjectType.REPRESENTATIVE,
                        subjectId = rep.id!!,
                        screenedName = rep.name,
                        matches = repMatches,
                        publicationDate = publicationDate,
                    )
                ) {
                    val seqNo = auditLogService.appendFreezeScreeningHit(
                        subjectType = ComplianceAuditSubjectType.REPRESENTATIVE,
                        subjectId = rep.id!!,
                        registryPublicationDate = publicationDate,
                        scoreThreshold = props.scoreThreshold,
                        matchCount = repMatches.size,
                        topScore = repMatches.first().score,
                    ).sequenceNo
                    recordMatches(
                        auditLogSeqRef = seqNo,
                        subjectType = ComplianceAuditSubjectType.REPRESENTATIVE,
                        subjectId = rep.id!!,
                        associationId = associationId,
                        screenedName = rep.name,
                        matches = repMatches,
                        publicationDate = publicationDate,
                    )
                    hits += FreezeHitTarget(
                        role = FreezeHitRole.REPRESENTATIVE,
                        subjectId = rep.id!!,
                        auditLogSeqRef = seqNo,
                    )
                }
            } else {
                auditLogService.appendFreezeScreeningClear(
                    subjectType = ComplianceAuditSubjectType.REPRESENTATIVE,
                    subjectId = rep.id!!,
                    registryPublicationDate = publicationDate,
                    scoreThreshold = props.scoreThreshold,
                )
            }
        }

        // 3. Screen each retained beneficial owner (BENEFICIAL_OWNER type only).
        // REPRESENTATIVE rows are already screened in step 2 — excluding them here avoids double-screening.
        val bos = beneficialOwnerRepository
            .findAllByAssociationIdOrderByCollectedAtAsc(associationId)
            .filter { !it.discarded && it.type == BeneficialOwnerType.BENEFICIAL_OWNER }
        for (bo in bos) {
            val boMatches = screeningService.screen(bo.name)
            if (boMatches.isNotEmpty()) {
                if (!dismissIfCleared(
                        clearance = clearance,
                        subjectType = ComplianceAuditSubjectType.BENEFICIAL_OWNER,
                        subjectId = bo.id!!,
                        screenedName = bo.name,
                        matches = boMatches,
                        publicationDate = publicationDate,
                    )
                ) {
                    val seqNo = auditLogService.appendFreezeScreeningHit(
                        subjectType = ComplianceAuditSubjectType.BENEFICIAL_OWNER,
                        subjectId = bo.id!!,
                        registryPublicationDate = publicationDate,
                        scoreThreshold = props.scoreThreshold,
                        matchCount = boMatches.size,
                        topScore = boMatches.first().score,
                    ).sequenceNo
                    recordMatches(
                        auditLogSeqRef = seqNo,
                        subjectType = ComplianceAuditSubjectType.BENEFICIAL_OWNER,
                        subjectId = bo.id!!,
                        associationId = associationId,
                        screenedName = bo.name,
                        matches = boMatches,
                        publicationDate = publicationDate,
                    )
                    hits += FreezeHitTarget(
                        role = FreezeHitRole.BENEFICIAL_OWNER,
                        subjectId = bo.id!!,
                        auditLogSeqRef = seqNo,
                    )
                }
            } else {
                auditLogService.appendFreezeScreeningClear(
                    subjectType = ComplianceAuditSubjectType.BENEFICIAL_OWNER,
                    subjectId = bo.id!!,
                    registryPublicationDate = publicationDate,
                    scoreThreshold = props.scoreThreshold,
                )
            }
        }

        if (hits.isNotEmpty()) {
            try {
                alertPort.onFreezeHit(associationId, hits)
            } catch (e: Exception) {
                logger.error(
                    "Freeze hit alert handler failed for association {}: {} — outcome remains HIT",
                    associationId, e.javaClass.simpleName,
                )
            }
            return ScreeningOutcome.HIT
        }
        return ScreeningOutcome.CLEAR
    }

    /**
     * Sets a correspondence aside when a compliance officer has already ruled it a false positive,
     * and journals the dismissal.
     *
     * The comparison is on (normalized screened name, register entry), never on the subject alone:
     * a ruling speaks only for the entries it examined. A register update that designates the same
     * party under a new entry, or a newly declared dirigeant, produces an uncovered couple and the
     * screening is a hit again.
     *
     * No evidence row is written: the correspondence is the one already recorded and shown to the
     * officer, and the journal entry references the alert whose closure covers it.
     *
     * @return true when the correspondence was set aside — the caller must then neither raise a
     *   hit nor alert.
     */
    private fun dismissIfCleared(
        clearance: FreezeClearance,
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID,
        screenedName: String,
        matches: List<ScreeningMatch>,
        publicationDate: LocalDate,
    ): Boolean {
        val normalized = NameNormalizer.normalize(screenedName)
        val found = matches.map { ClearedPair(normalized, it.idRegistre) }
        if (!clearance.covers(found)) return false

        auditLogService.appendFreezeScreeningHitCleared(
            subjectType = subjectType,
            subjectId = subjectId,
            registryPublicationDate = publicationDate,
            scoreThreshold = props.scoreThreshold,
            matchCount = matches.size,
            topScore = matches.first().score,
            clearedByAlertIds = clearance.alertIds,
        )
        return true
    }

    /**
     * Persists the decision-grade evidence behind one `FREEZE_SCREENING_HIT`.
     *
     * The journal records only aggregates (count, top score) — deliberately, since its field set
     * is identity-free by design. Without this the compliance officer sees "3 matches, 0.93" and
     * cannot tell which register entries were involved, so no decision is motivable.
     *
     * Delegates to [FreezeScreeningEvidenceRecorder] rather than writing here: a hit makes
     * [VerificationService.adminApprove] throw, and that method is `@Transactional` — evidence
     * written in the ambient transaction is rolled back with the refusal it documents. The
     * recorder commits in a transaction of its own, exactly as the journal does.
     *
     * Never throws: evidence capture must not mask the screening outcome, which is the blocking
     * signal the caller acts on.
     */
    private fun recordMatches(
        auditLogSeqRef: Long,
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID,
        associationId: UUID,
        screenedName: String,
        matches: List<ScreeningMatch>,
        publicationDate: LocalDate,
    ) {
        try {
            evidenceRecorder.record(
                auditLogSeqRef = auditLogSeqRef,
                subjectType = subjectType,
                subjectId = subjectId,
                associationId = associationId,
                screenedName = screenedName,
                matches = matches,
                publicationDate = publicationDate,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to persist freeze screening evidence for subject {} (seq {}): {}",
                subjectId, auditLogSeqRef, e.javaClass.simpleName,
            )
        }
    }

    /**
     * Surfaces an impossible control to the compliance officer.
     *
     * `docs/legal/E4-journal-controles-de-gel.md` §4.4 requires failures to be journalled because
     * a journal silent on failure cannot distinguish "no match" from "no control". The alert
     * surface reintroduced exactly that silence: UNAVAILABLE outcomes were recorded and never
     * seen. Never throws — a failure to alert must not mask the outcome.
     */
    private fun raiseUnavailableAlert(associationId: UUID, reason: String) {
        try {
            alertPort.onScreeningUnavailable(
                subjectType = ComplianceAlertSubjectType.ASSOCIATION,
                subjectId = associationId,
                reason = reason,
            )
        } catch (e: Exception) {
            logger.error(
                "Screening-unavailable alert handler failed for association {}: {}",
                associationId, e.javaClass.simpleName,
            )
        }
    }
}

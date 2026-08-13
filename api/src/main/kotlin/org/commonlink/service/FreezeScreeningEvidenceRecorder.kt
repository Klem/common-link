package org.commonlink.service

import org.commonlink.config.SanctionsProperties
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.FreezeScreeningMatch
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.commonlink.util.NameNormalizer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Persists the decision-grade evidence behind a `FREEZE_SCREENING_HIT`, in its own transaction.
 *
 * ### Why this is a separate bean, and why REQUIRES_NEW
 *
 * A freeze hit is, by construction, recorded on a path that is about to fail: the caller screens,
 * finds a match, and *refuses the operation* — `VerificationService.adminApprove` throws
 * `ConflictException`, `PublicWidgetService.createDonation` likewise. Written in the ambient
 * transaction, `saveAll()` only queues an INSERT for flush-at-commit; the commit never happens and
 * the rollback takes the evidence with it. The symptom is silent and total: the journal keeps the
 * hit (it writes in [Propagation.REQUIRES_NEW]) while the correspondence table stays empty, so the
 * compliance officer reads "3 matches, top score 0.93" and cannot name a single register entry.
 *
 * The evidence therefore needs exactly what the journal already has — a transaction of its own,
 * committed independently of the caller's outcome. Spring's proxying makes that impossible from a
 * private method of the screening service (self-invocation bypasses the interceptor), hence a bean.
 *
 * ### Why this method does not catch
 *
 * Evidence capture must never mask a screening outcome, but the swallow belongs to the *caller*:
 * catching inside a transactional method would let Spring commit a persistence context Hibernate
 * may already have marked as failed.
 */
@Service
class FreezeScreeningEvidenceRecorder(
    private val matchRepository: FreezeScreeningMatchRepository,
    private val props: SanctionsProperties,
) {

    /**
     * Writes one row per correspondence and commits, whatever the caller does next.
     *
     * [screenedName] is normalized here with the same [NameNormalizer] the screening used, so the
     * stored value is exactly the string that produced the score — not the raw dossier name, which
     * would leave the score unexplained ("TECHNO +" scoring 0.93 against "TECHNOLAB" only makes
     * sense once you see it was compared as "TECHNO").
     *
     * @param auditLogSeqRef `sequence_no` of the journal entry that constated the hit.
     * @param associationId  Grouping context; null for a donor screening, which has none.
     * @throws org.springframework.dao.DataAccessException if the write fails — the caller is
     *   responsible for logging and continuing, since the screening outcome must still be returned.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        auditLogSeqRef: Long,
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID,
        associationId: UUID?,
        screenedName: String,
        matches: List<ScreeningMatch>,
        publicationDate: LocalDate,
    ) {
        // Spares the normalization and the write, not the transaction: the interceptor has already
        // run. Both current callers record only on a non-empty match set.
        if (matches.isEmpty()) return
        val normalized = NameNormalizer.normalize(screenedName)
        matchRepository.saveAll(
            matches.map { match ->
                FreezeScreeningMatch(
                    auditLogSeqRef = auditLogSeqRef,
                    subjectType = subjectType,
                    subjectId = subjectId,
                    associationId = associationId,
                    screenedNormalizedName = normalized,
                    sanctionedIdRegistre = match.idRegistre,
                    matchedName = match.nom,
                    matchedNature = match.nature,
                    matchedLegalReference = match.legalReference,
                    matchedDateOfBirth = match.dateOfBirth,
                    score = match.score,
                    scoreThreshold = props.scoreThreshold,
                    registryPublicationDate = publicationDate,
                )
            },
        )
    }
}

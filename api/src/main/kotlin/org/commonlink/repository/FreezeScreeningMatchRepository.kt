package org.commonlink.repository

import org.commonlink.entity.FreezeScreeningMatch
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Read/write access to the decision-grade evidence backing a freeze-register correspondence.
 *
 * Written once at screening time by [org.commonlink.service.FreezeScreeningOnboardingService]
 * and [org.commonlink.service.FreezeScreeningDonationService]; read by
 * [org.commonlink.controller.ComplianceController] when the officer opens an alert.
 */
interface FreezeScreeningMatchRepository : JpaRepository<FreezeScreeningMatch, UUID> {

    /**
     * All correspondences recorded in the context of one association — the association's own
     * screening plus those of its representatives and beneficial owners.
     *
     * This is the query behind an alert whose subject is an association: a representative's
     * correspondence is journalled against `subject_id = representative.id`, while the alert it
     * raises carries `subject_id = associationId` (see
     * [org.commonlink.service.FreezeHitAlertAdapter]). Only [FreezeScreeningMatch.associationId]
     * bridges the two.
     */
    fun findByAssociationIdOrderByScoreDesc(associationId: UUID): List<FreezeScreeningMatch>

    /** All correspondences recorded against one screened party directly (beneficial owner, donor). */
    fun findBySubjectIdOrderByScoreDesc(subjectId: UUID): List<FreezeScreeningMatch>

    /**
     * Same scope as [findByAssociationIdOrderByScoreDesc], bounded to the evidence recorded up to
     * a given journal position — the correspondences an officer could actually see when ruling.
     *
     * The bound is what keeps a clearance honest: without it the evidence of the very screening
     * being evaluated would be counted as already cleared, and every subsequent run would clear
     * itself. See [org.commonlink.service.FreezeClearanceService].
     */
    fun findByAssociationIdAndAuditLogSeqRefLessThanEqual(
        associationId: UUID,
        auditLogSeqRef: Long,
    ): List<FreezeScreeningMatch>

    /**
     * The correspondences recorded from a given journal position onward — the evidence of one
     * screening run, used to ask whether that run's findings have all been ruled upon.
     */
    fun findByAssociationIdAndAuditLogSeqRefGreaterThanEqual(
        associationId: UUID,
        auditLogSeqRef: Long,
    ): List<FreezeScreeningMatch>

    /** Same scope as [findBySubjectIdOrderByScoreDesc], bounded to a journal position. */
    fun findBySubjectIdAndAuditLogSeqRefLessThanEqual(
        subjectId: UUID,
        auditLogSeqRef: Long,
    ): List<FreezeScreeningMatch>
}

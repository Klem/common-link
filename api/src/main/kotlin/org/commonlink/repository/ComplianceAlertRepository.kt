package org.commonlink.repository

import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertDecision
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ComplianceAlertRepository : JpaRepository<ComplianceAlert, UUID> {

    /**
     * Looks for an open (non-CLOSED) alert for a non-null subject.
     * Used by [org.commonlink.service.ComplianceAlertService.createOrIgnore] to enforce idempotency.
     */
    fun findByOriginAndSubjectIdAndStatusIn(
        origin: ComplianceAlertOrigin,
        subjectId: UUID,
        statuses: Collection<ComplianceAlertStatus>,
    ): ComplianceAlert?

    /**
     * Looks for an open (non-CLOSED) alert for a SYSTEM-level origin where subject_id IS NULL.
     * Used by [org.commonlink.service.ComplianceAlertService.createOrIgnore] to enforce idempotency.
     */
    fun findByOriginAndSubjectIdIsNullAndStatusIn(
        origin: ComplianceAlertOrigin,
        statuses: Collection<ComplianceAlertStatus>,
    ): ComplianceAlert?

    /**
     * Paginates alerts whose origin is in the provided set, most recent first.
     * Used by the compliance officer list screen (prompt 17) — filters to freeze-hit origins only.
     */
    fun findByOriginIn(origins: Collection<ComplianceAlertOrigin>, pageable: Pageable): Page<ComplianceAlert>

    /**
     * Counts alerts still open (PENDING or IN_REVIEW) among the given origins.
     * Backs the dashboard's "alertes en attente" tile, which must exclude closed alerts.
     */
    fun countByOriginInAndStatusIn(
        origins: Collection<ComplianceAlertOrigin>,
        statuses: Collection<ComplianceAlertStatus>,
    ): Long

    /**
     * Alerts already ruled on for the same subject, most recent first.
     * Backs the informative prior-decision banner on the alert detail screen.
     */
    fun findBySubjectIdAndStatusOrderByCreatedAtDesc(
        subjectId: UUID,
        status: ComplianceAlertStatus,
    ): List<ComplianceAlert>

    /**
     * Alerts in a given status for one subject and origin, whatever the decision.
     *
     * Backs [org.commonlink.service.FreezeClearanceService], which must see **every** closure and
     * not only the favorable ones: filtering on `FALSE_POSITIVE` here would hide a later
     * `SUSPICIOUS` ruling, and a stale false positive would keep lifting a correspondence the
     * officer has since confirmed.
     */
    fun findBySubjectIdAndOriginAndStatus(
        subjectId: UUID,
        origin: ComplianceAlertOrigin,
        status: ComplianceAlertStatus,
    ): List<ComplianceAlert>

    /**
     * The next alert of the same (subject, origin) opened after [auditLogSeqRef], if any — the
     * one immediately following it in the journal.
     *
     * Backs the upper bound of a per-alert journal-history window: since the partial unique index
     * `compliance_alert_pending_dedup_uq` guarantees at most one open alert per (origin, subject)
     * at a time, alerts for the same pair are strictly ordered by [ComplianceAlert.auditLogSeqRef].
     * Without this, a report journaled *after* the current alert closed (and belonging to the next
     * one) would appear on the current, already-ruled-on alert's detail screen.
     */
    fun findFirstBySubjectIdAndOriginAndAuditLogSeqRefGreaterThanOrderByAuditLogSeqRefAsc(
        subjectId: UUID,
        origin: ComplianceAlertOrigin,
        auditLogSeqRef: Long,
    ): ComplianceAlert?
}

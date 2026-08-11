package org.commonlink.repository

import org.commonlink.entity.ComplianceAlert
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
}

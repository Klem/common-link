package org.commonlink.service

import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertStatus
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.ComplianceAlertRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Sole writer of [org.commonlink.entity.AssociationProfile.status] (IC-44 — canal de signalement
 * de campagne). Every transition is derived from the association's current state, never assigned
 * unconditionally, so a late-arriving call can't regress `SUSPENDED` back to `ALERT` or reopen a
 * status that already moved on.
 *
 * Kept separate from [ComplianceAlertService] to avoid a constructor cycle: this service reads
 * [ComplianceAlertRepository] directly (not through [ComplianceAlertService]) so that
 * `ComplianceAlertService.close()` can depend on this service without a circular dependency.
 */
@Service
class AssociationComplianceStatusService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val complianceAlertRepository: ComplianceAlertRepository,
    private val auditLog: ComplianceAuditLogService,
) {
    companion object {
        private val OPEN_STATUSES = listOf(ComplianceAlertStatus.PENDING, ComplianceAlertStatus.IN_REVIEW)
    }

    /**
     * `ACTIVE → ALERT` when a campaign report is received. A no-op when the association is
     * already `ALERT` (a second report while the first is under review) or `SUSPENDED` (a report
     * must never lift a suspension already in force).
     */
    @Transactional
    fun raiseAlert(associationId: UUID) {
        val association = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId introuvable") }
        if (association.status == AssociationStatus.ACTIVE) {
            association.status = AssociationStatus.ALERT
            associationProfileRepository.save(association)
        }
    }

    /** `→ SUSPENDED`, unconditionally: a confirmed report always suspends, regardless of the prior state. */
    @Transactional
    fun suspend(associationId: UUID) {
        val association = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId introuvable") }
        association.status = AssociationStatus.SUSPENDED
        associationProfileRepository.save(association)
    }

    /**
     * `ALERT → ACTIVE` when a report is closed as unfounded, but only if no other
     * `CAMPAIGN_REPORT` alert is still open on this association — a second, still-pending report
     * must not be cleared by the first one's dismissal. A no-op outside `ALERT` (e.g. already
     * `ACTIVE`, or `SUSPENDED` from an earlier confirmed report — dismissing a later, unrelated
     * report must never lift that suspension).
     */
    @Transactional
    fun clearAlertIfNoneOpen(associationId: UUID) {
        val association = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId introuvable") }
        if (association.status != AssociationStatus.ALERT) return
        val stillOpen = complianceAlertRepository.findByOriginAndSubjectIdAndStatusIn(
            ComplianceAlertOrigin.CAMPAIGN_REPORT, associationId, OPEN_STATUSES,
        ) != null
        if (!stillOpen) {
            association.status = AssociationStatus.ACTIVE
            associationProfileRepository.save(association)
        }
    }

    /**
     * `SUSPENDED → ACTIVE` on a compliance officer's decision. Does **not** touch the
     * [org.commonlink.entity.ComplianceAlert] that caused the suspension — it stays `CLOSED` with
     * its `SUSPICIOUS` decision intact, as the historical record of what was found and ruled.
     * Reactivation is a distinct, later fact, journaled on its own
     * ([ComplianceAuditLogService.ASSOCIATION_REACTIVATED]).
     *
     * @throws UnprocessableEntityException if the association is not currently `SUSPENDED`, or if
     *   [rationale] is blank.
     */
    @Transactional
    fun reactivate(associationId: UUID, actorUserId: UUID, rationale: String) {
        if (rationale.isBlank()) throw UnprocessableEntityException("La motivation de la réactivation est obligatoire")
        val association = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId introuvable") }
        if (association.status != AssociationStatus.SUSPENDED) {
            throw UnprocessableEntityException("L'association n'est pas suspendue")
        }
        association.status = AssociationStatus.ACTIVE
        associationProfileRepository.save(association)

        auditLog.append(
            eventType = ComplianceAuditLogService.ASSOCIATION_REACTIVATED,
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = associationId,
            actorUserId = actorUserId,
            payload = mapOf("rationale" to rationale),
        )
    }
}

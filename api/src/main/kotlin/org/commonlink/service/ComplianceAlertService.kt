package org.commonlink.service

import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertDecision
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertStatus
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.ComplianceAlertRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Manages the lifecycle of LCB-FT compliance alerts raised by automated controls.
 *
 * ### Idempotency
 * [createOrIgnore] checks for an existing open (PENDING or IN_REVIEW) alert for the same
 * (origin, subject) pair before inserting. If one exists it is returned unchanged and no
 * duplicate journal entry is written. The partial unique index `compliance_alert_pending_dedup_uq`
 * (V61) is the database-level backstop for race conditions.
 *
 * ### Transaction model
 * [createOrIgnore] runs in `REQUIRES_NEW` so the alert row is committed even when the caller's
 * transaction is subsequently rolled back (e.g. [org.commonlink.service.VerificationService]
 * throws [org.commonlink.exception.ConflictException] after the freeze-screening HIT). This
 * mirrors the pattern used by every `appendFreezeScreening*` helper in
 * [ComplianceAuditLogService].
 *
 * [takeInCharge] and [close] run in the caller's transaction (REQUIRED): alert update and
 * audit log entry commit atomically or not at all.
 *
 * ### Scope
 * No controller outside the compliance namespace (`/api/compliance/`) may call this service
 * directly. Screens come in prompts 17 and 20.
 */
@Service
class ComplianceAlertService(
    private val repo: ComplianceAlertRepository,
    private val auditLog: ComplianceAuditLogService,
) {

    companion object {
        private val OPEN_STATUSES = listOf(ComplianceAlertStatus.PENDING, ComplianceAlertStatus.IN_REVIEW)
    }

    /**
     * Creates a new alert for the given (origin, subject) pair, or returns the existing open alert
     * if one already exists — ensuring no duplicate open alert is raised for the same subject.
     *
     * Runs in a **new, independent transaction** so the alert is committed even when the caller's
     * transaction rolls back. Each unique open alert creation writes one `ALERT_OPENED` entry to
     * the compliance audit journal; duplicate calls write nothing.
     *
     * @param auditLogSeqRef sequence_no of the compliance_audit_log entry that triggered this
     *   alert, for traceability. Null when not available at call site.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createOrIgnore(
        origin: ComplianceAlertOrigin,
        subjectType: ComplianceAlertSubjectType,
        subjectId: UUID?,
        severity: ComplianceAlertSeverity,
        auditLogSeqRef: Long? = null,
    ): ComplianceAlert {
        val existing = findOpenAlert(origin, subjectId)
        if (existing != null) return existing

        val alert = repo.save(
            ComplianceAlert(
                origin = origin,
                subjectType = subjectType,
                subjectId = subjectId,
                severity = severity,
                createdAt = Instant.now(),
                auditLogSeqRef = auditLogSeqRef,
            ),
        )

        auditLog.append(
            eventType = ComplianceAuditLogService.ALERT_OPENED,
            subjectType = ComplianceAuditSubjectType.ALERT,
            subjectId = alert.id,
            payload = mapOf(
                "origin" to origin.name,
                "alertSubjectType" to subjectType.name,
                "alertSubjectId" to subjectId?.toString(),
                "severity" to severity.name,
            ),
        )

        return alert
    }

    /**
     * Transitions an alert from PENDING to IN_REVIEW, recording the compliance officer and
     * timestamp. Writes an `ALERT_IN_REVIEW` journal entry.
     *
     * @throws NotFoundException if the alert does not exist.
     * @throws UnprocessableEntityException if the current status does not allow this transition.
     */
    @Transactional
    fun takeInCharge(alertId: UUID, complianceOfficerUserId: UUID): ComplianceAlert {
        val alert = repo.findById(alertId).orElseThrow { NotFoundException("Alerte $alertId introuvable") }
        validateStatusTransition(alert.status, ComplianceAlertStatus.IN_REVIEW)

        alert.status = ComplianceAlertStatus.IN_REVIEW
        alert.takenInChargeAt = Instant.now()
        alert.takenInChargeBy = complianceOfficerUserId

        val saved = repo.save(alert)

        auditLog.append(
            eventType = ComplianceAuditLogService.ALERT_IN_REVIEW,
            subjectType = ComplianceAuditSubjectType.ALERT,
            subjectId = alertId,
            actorUserId = complianceOfficerUserId,
            payload = mapOf("alertId" to alertId.toString()),
        )

        return saved
    }

    /**
     * Transitions an alert from IN_REVIEW to CLOSED, recording the decision and rationale.
     * Writes an `ALERT_CLOSED` journal entry.
     *
     * @param decision the compliance outcome (LEGITIMATE / SUSPICIOUS / FALSE_POSITIVE).
     * @param rationale mandatory justification for the decision; must not be blank.
     * @throws NotFoundException if the alert does not exist.
     * @throws UnprocessableEntityException if the current status does not allow this transition.
     */
    @Transactional
    fun close(alertId: UUID, complianceOfficerUserId: UUID, decision: ComplianceAlertDecision, rationale: String): ComplianceAlert {
        if (rationale.isBlank()) throw UnprocessableEntityException("La motivation de la décision est obligatoire")
        val alert = repo.findById(alertId).orElseThrow { NotFoundException("Alerte $alertId introuvable") }
        validateStatusTransition(alert.status, ComplianceAlertStatus.CLOSED)

        alert.status = ComplianceAlertStatus.CLOSED
        alert.decision = decision
        alert.decisionRationale = rationale

        val saved = repo.save(alert)

        auditLog.append(
            eventType = ComplianceAuditLogService.ALERT_CLOSED,
            subjectType = ComplianceAuditSubjectType.ALERT,
            subjectId = alertId,
            actorUserId = complianceOfficerUserId,
            payload = mapOf("decision" to decision.name),
        )

        return saved
    }

    /**
     * Enforces the allowed status transitions.
     *
     * ```
     * PENDING → IN_REVIEW
     * IN_REVIEW → CLOSED
     * CLOSED → (terminal — no transition allowed)
     * ```
     *
     * @throws UnprocessableEntityException on a forbidden transition.
     */
    fun validateStatusTransition(current: ComplianceAlertStatus, next: ComplianceAlertStatus) {
        val allowed = when (current) {
            ComplianceAlertStatus.PENDING -> next == ComplianceAlertStatus.IN_REVIEW
            ComplianceAlertStatus.IN_REVIEW -> next == ComplianceAlertStatus.CLOSED
            ComplianceAlertStatus.CLOSED -> false
        }
        if (!allowed) {
            throw UnprocessableEntityException(
                "Transition de statut d'alerte non autorisée : $current → $next",
            )
        }
    }

    private fun findOpenAlert(origin: ComplianceAlertOrigin, subjectId: UUID?): ComplianceAlert? =
        if (subjectId != null) {
            repo.findByOriginAndSubjectIdAndStatusIn(origin, subjectId, OPEN_STATUSES)
        } else {
            repo.findByOriginAndSubjectIdIsNullAndStatusIn(origin, OPEN_STATUSES)
        }
}

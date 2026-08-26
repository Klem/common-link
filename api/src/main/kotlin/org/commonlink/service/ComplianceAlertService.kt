package org.commonlink.service

import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertDecision
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertStatus
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.event.ComplianceAlertOpenedEvent
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.ComplianceAlertRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    private val eventPublisher: ApplicationEventPublisher,
    private val associationComplianceStatusService: AssociationComplianceStatusService,
) {

    companion object {
        private val OPEN_STATUSES = listOf(ComplianceAlertStatus.PENDING, ComplianceAlertStatus.IN_REVIEW)

        /**
         * Origins surfaced on the compliance officer's freeze screens.
         *
         * [ComplianceAlertOrigin.SCREENING_UNAVAILABLE] belongs here for the same reason
         * `docs/legal/E4-journal-controles-de-gel.md` §4.4 makes failure records mandatory: a
         * surface that shows only the controls that succeeded cannot distinguish "no match" from
         * "no control", and is therefore misleading.
         */
        val FREEZE_ORIGINS = listOf(
            ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
            ComplianceAlertOrigin.FREEZE_HIT_DONATION,
            ComplianceAlertOrigin.SCREENING_UNAVAILABLE,
        )

        /** Origins surfaced on the compliance officer's campaign-report screen (IC-44). */
        val CAMPAIGN_REPORT_ORIGINS = listOf(ComplianceAlertOrigin.CAMPAIGN_REPORT)
    }

    /**
     * Returns a paginated list of freeze-related alerts (hits and unavailable screenings),
     * ordered by the provided [pageable]. Intended for the compliance officer list screen.
     */
    @Transactional(readOnly = true)
    fun listFreezeHitAlerts(pageable: Pageable): Page<ComplianceAlert> =
        repo.findByOriginIn(FREEZE_ORIGINS, pageable)

    /**
     * Counts freeze-related alerts still awaiting treatment (PENDING or IN_REVIEW).
     *
     * Distinct from the total returned by [listFreezeHitAlerts]: the dashboard tile labelled
     * "alertes en attente" must not count closed alerts, or it reports a permanently growing
     * backlog that no amount of treatment reduces.
     */
    @Transactional(readOnly = true)
    fun countOpenFreezeAlerts(): Long =
        repo.countByOriginInAndStatusIn(FREEZE_ORIGINS, OPEN_STATUSES)

    /** Returns a paginated list of campaign-report alerts (IC-44), ordered by the provided [pageable]. */
    @Transactional(readOnly = true)
    fun listCampaignReportAlerts(pageable: Pageable): Page<ComplianceAlert> =
        repo.findByOriginIn(CAMPAIGN_REPORT_ORIGINS, pageable)

    /** Counts campaign-report alerts still awaiting treatment (PENDING or IN_REVIEW). */
    @Transactional(readOnly = true)
    fun countOpenCampaignReportAlerts(): Long =
        repo.countByOriginInAndStatusIn(CAMPAIGN_REPORT_ORIGINS, OPEN_STATUSES)

    /**
     * Returns the closed alerts previously ruled on for the same subject, most recent first.
     *
     * Purely informative: closure is irreversible and a fresh correspondence always raises a new
     * alert (`docs/legal/E4-traitement-alerte-et-information-tresor.md` §4.1). Showing the prior
     * ruling spares the officer from re-deriving an identical analysis on every donation, without
     * suppressing anything automatically — a whitelist surviving a register change would be an
     * LCB-FT hole.
     */
    @Transactional(readOnly = true)
    fun findPriorDecisions(subjectId: UUID, excludingAlertId: UUID): List<ComplianceAlert> =
        repo.findBySubjectIdAndStatusOrderByCreatedAtDesc(subjectId, ComplianceAlertStatus.CLOSED)
            .filter { it.id != excludingAlertId }

    /**
     * Returns a single alert by id or throws [NotFoundException].
     */
    @Transactional(readOnly = true)
    fun findById(alertId: UUID): ComplianceAlert =
        repo.findById(alertId).orElseThrow { NotFoundException("Alerte $alertId introuvable") }

    /**
     * The next alert of the same (subject, origin) opened after [auditLogSeqRef], if any.
     *
     * Used to bound the journal-history window shown for one alert to its own lifetime — see
     * [ComplianceAlertRepository.findFirstBySubjectIdAndOriginAndAuditLogSeqRefGreaterThanOrderByAuditLogSeqRefAsc].
     */
    @Transactional(readOnly = true)
    fun findNextAlert(subjectId: UUID, origin: ComplianceAlertOrigin, auditLogSeqRef: Long): ComplianceAlert? =
        repo.findFirstBySubjectIdAndOriginAndAuditLogSeqRefGreaterThanOrderByAuditLogSeqRefAsc(subjectId, origin, auditLogSeqRef)

    /**
     * Creates a new alert for the given (origin, subject) pair, or returns the existing open alert
     * if one already exists — ensuring no duplicate open alert is raised for the same subject.
     *
     * Runs in a **new, independent transaction** so the alert is committed even when the caller's
     * transaction rolls back. Each unique open alert creation writes one `ALERT_OPENED` entry to
     * the compliance audit journal; duplicate calls write nothing.
     *
     * Publishes [org.commonlink.event.ComplianceAlertOpenedEvent] on insertion only — never on the
     * idempotent return — so subscribers such as [ComplianceAlertEmailListener] fire exactly once per
     * open alert. Consumed `AFTER_COMMIT` against this method's own transaction.
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

        // Published only on insertion, never on the idempotent early return above: a subscriber must
        // not be woken twice for the same open alert. Consumed AFTER_COMMIT, so no notification can
        // go out for an alert row that never landed.
        eventPublisher.publishEvent(
            ComplianceAlertOpenedEvent(
                alertId = alert.id!!,
                origin = origin,
                subjectType = subjectType,
                subjectId = subjectId,
                severity = severity,
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
     * @param treasuryNotifiedAt when the DG Trésor was notified (human gesture, proof only).
     *   Required when [decision] is [ComplianceAlertDecision.SUSPICIOUS] **and** the alert's
     *   origin is one of [FREEZE_ORIGINS] — DG Trésor notification is an asset-freeze obligation,
     *   not a general consequence of a SUSPICIOUS ruling (a campaign-report alert has nothing to
     *   notify the Treasury about).
     * @param treasuryNotificationMethod channel used for notification; required under the same condition.
     * @param treasuryNotificationRef reference assigned to the notification; required under the same condition.
     * @throws NotFoundException if the alert does not exist.
     * @throws UnprocessableEntityException if the current status does not allow this transition,
     *   if [rationale] is blank, or if treasury fields are missing for a SUSPICIOUS freeze-origin decision.
     */
    @Transactional
    fun close(
        alertId: UUID,
        complianceOfficerUserId: UUID,
        decision: ComplianceAlertDecision,
        rationale: String,
        treasuryNotifiedAt: Instant? = null,
        treasuryNotificationMethod: String? = null,
        treasuryNotificationRef: String? = null,
    ): ComplianceAlert {
        if (rationale.isBlank()) throw UnprocessableEntityException("La motivation de la décision est obligatoire")
        val alert = repo.findById(alertId).orElseThrow { NotFoundException("Alerte $alertId introuvable") }
        if (decision == ComplianceAlertDecision.SUSPICIOUS && alert.origin in FREEZE_ORIGINS &&
            (treasuryNotifiedAt == null || treasuryNotificationMethod.isNullOrBlank() || treasuryNotificationRef.isNullOrBlank())
        ) {
            throw UnprocessableEntityException("La traçabilité de la notification à la DG Trésor est obligatoire pour une décision de correspondance avérée")
        }
        validateStatusTransition(alert.status, ComplianceAlertStatus.CLOSED)

        alert.status = ComplianceAlertStatus.CLOSED
        alert.decision = decision
        alert.decisionRationale = rationale
        alert.treasuryNotifiedAt = treasuryNotifiedAt
        alert.treasuryNotificationMethod = treasuryNotificationMethod
        alert.treasuryNotificationRef = treasuryNotificationRef

        val saved = repo.save(alert)

        auditLog.append(
            eventType = ComplianceAuditLogService.ALERT_CLOSED,
            subjectType = ComplianceAuditSubjectType.ALERT,
            subjectId = alertId,
            actorUserId = complianceOfficerUserId,
            payload = mapOf(
                "decision" to decision.name,
                "treasuryNotified" to (treasuryNotifiedAt != null),
            ),
        )

        // Association status side effect — CAMPAIGN_REPORT only. SUSPICIOUS confirms the report:
        // suspend. LEGITIMATE/FALSE_POSITIVE dismisses it: clear the ALERT flag, but only if no
        // other open report still stands (see AssociationComplianceStatusService.clearAlertIfNoneOpen).
        val subjectId = saved.subjectId
        if (saved.origin == ComplianceAlertOrigin.CAMPAIGN_REPORT && subjectId != null) {
            if (decision == ComplianceAlertDecision.SUSPICIOUS) {
                associationComplianceStatusService.suspend(subjectId)
            } else {
                associationComplianceStatusService.clearAlertIfNoneOpen(subjectId)
            }
        }

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

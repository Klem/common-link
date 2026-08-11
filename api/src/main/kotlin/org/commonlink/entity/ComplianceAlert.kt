package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class ComplianceAlertOrigin {
    FREEZE_HIT_ONBOARDING,
    FREEZE_HIT_DONATION,
    SYNC_FAILURE,
    SPLIT_DETECTION,
    ATYPICALITY_RULE,
}

enum class ComplianceAlertSubjectType {
    ASSOCIATION,
    BENEFICIAL_OWNER,
    DONOR,
    SYSTEM,
}

enum class ComplianceAlertSeverity { HIGH, MEDIUM, LOW }

enum class ComplianceAlertStatus { PENDING, IN_REVIEW, CLOSED }

enum class ComplianceAlertDecision { LEGITIMATE, SUSPICIOUS, FALSE_POSITIVE }

/**
 * One compliance alert raised by an automated control (freeze screening, sync failure, etc.).
 *
 * Only [org.commonlink.service.ComplianceAlertService] creates and mutates instances.
 * The circumstantial detail is NOT stored here — it lives in the hash-chained journal
 * (`compliance_audit_log`, V51) referenced by [auditLogSeqRef].
 *
 * ### Lifecycle
 * PENDING → IN_REVIEW → CLOSED (terminal). No reversal. Enforced by
 * [org.commonlink.service.ComplianceAlertService.validateStatusTransition].
 *
 * ### Idempotency
 * The DB partial unique index `compliance_alert_pending_dedup_uq` and the service-level
 * pre-check both prevent a second open alert for the same (origin, subject) pair while
 * the first is still PENDING or IN_REVIEW.
 */
@Entity
@Table(name = "compliance_alert")
class ComplianceAlert(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false, length = 32)
    val origin: ComplianceAlertOrigin,

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false, length = 32)
    val subjectType: ComplianceAlertSubjectType,

    @Column(name = "subject_id", updatable = false)
    val subjectId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    val severity: ComplianceAlertSeverity,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: ComplianceAlertStatus = ComplianceAlertStatus.PENDING,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "taken_in_charge_at")
    var takenInChargeAt: Instant? = null,

    @Column(name = "taken_in_charge_by")
    var takenInChargeBy: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 16)
    var decision: ComplianceAlertDecision? = null,

    @Column(name = "decision_rationale", columnDefinition = "TEXT")
    var decisionRationale: String? = null,

    /** sequence_no of the compliance_audit_log entry that triggered this alert, or null if unknown. */
    @Column(name = "audit_log_seq_ref", updatable = false)
    val auditLogSeqRef: Long? = null,
)

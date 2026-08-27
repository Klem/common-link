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

    /**
     * A freeze screening could not be completed — empty register, missing dirigeant data, or a
     * technical failure. Raised alongside the `FREEZE_SCREENING_UNAVAILABLE` journal entry.
     *
     * `docs/legal/E4-journal-controles-de-gel.md` §4.4 makes recording failures mandatory
     * *because a journal that is silent on failure is misleading* — it cannot distinguish a
     * period without controls from a period of uniformly successful ones. Surfacing failures
     * only in the journal reintroduced that silence at the alert layer: an impossible control
     * was recorded and never seen. An UNAVAILABLE outcome is not a favorable outcome.
     */
    SCREENING_UNAVAILABLE,
    SYNC_FAILURE,
    SPLIT_DETECTION,
    ATYPICALITY_RULE,

    /**
     * A public visitor reported a campaign's content through the public report channel (IC-44).
     * Subject is always the owning [org.commonlink.entity.AssociationProfile] — a report is not
     * scoped to the single reported campaign, since [org.commonlink.entity.AssociationProfile.status]
     * suspension applies to the whole association's portfolio.
     */
    CAMPAIGN_REPORT,
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

    /** Date and time the DG Trésor was notified — human action, recorded as proof only. */
    @Column(name = "treasury_notified_at")
    var treasuryNotifiedAt: Instant? = null,

    /** Channel used for DG Trésor notification (e.g. "courriel sécurisé", "courrier recommandé"). */
    @Column(name = "treasury_notification_method", length = 128)
    var treasuryNotificationMethod: String? = null,

    /** Reference or identifier assigned to the DG Trésor notification (e.g. email ID, letter number). */
    @Column(name = "treasury_notification_ref", length = 256)
    var treasuryNotificationRef: String? = null,
)

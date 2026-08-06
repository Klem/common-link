package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One row of the LCB-FT compliance audit journal — the common, append-only proof-of-control
 * substrate shared by every feature that needs to demonstrate a check happened, when, and that
 * no row was removed or rewritten since (freeze screening, alert review, publication refusals).
 *
 * Rows are never updated or deleted: [org.commonlink.service.ComplianceAuditLogService] is the
 * sole write path, and the V51 migration enforces this at the database level (revoked
 * privileges + a `BEFORE UPDATE OR DELETE` trigger). [sequenceNo] — not [occurredAt] — orders the
 * hash chain, because two writes can share the same millisecond.
 *
 * [eventType] is deliberately free text with no `CHECK` constraint: this journal is shared by
 * features that don't exist yet, and constraining the value set here would force every future
 * feature to modify this migration, recreating the coupling this substrate exists to remove.
 * [subjectType], by contrast, is a closed set of business object kinds known today and is backed
 * by a `CHECK` constraint mirroring [ComplianceAuditSubjectType].
 */
@Entity
@Table(name = "compliance_audit_log")
class ComplianceAuditLog(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** Strictly increasing value from `compliance_audit_log_seq`; orders the hash chain. */
    @Column(name = "sequence_no", nullable = false, updatable = false, unique = true)
    val sequenceNo: Long,

    /** Free-text event kind, defined by the calling feature (e.g. `FREEZE_SCREENING_HIT`). */
    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    val eventType: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false, length = 32)
    val subjectType: ComplianceAuditSubjectType,

    @Column(name = "subject_id", updatable = false)
    val subjectId: UUID? = null,

    /** JSON detail of the event. Never a secret, key, or télédéclarant number — see KDoc on the service. */
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    val payload: String,

    /** [org.commonlink.entity.User.id] of the actor, or null for an automated process. */
    @Column(name = "actor_user_id", updatable = false)
    val actorUserId: UUID? = null,

    @Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: Instant,

    /** SHA-256 hex digest of the previous row, or null for the very first row only. */
    @Column(name = "prev_hash", updatable = false, length = 64)
    val prevHash: String? = null,

    @Column(name = "row_hash", nullable = false, updatable = false, length = 64)
    val rowHash: String,
)

/** Closed set of business object kinds a compliance audit event can target. */
enum class ComplianceAuditSubjectType { ASSOCIATION, DONATION, CAMPAIGN, ALERT }

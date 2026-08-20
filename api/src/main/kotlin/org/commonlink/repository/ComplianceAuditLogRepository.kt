package org.commonlink.repository

import org.commonlink.entity.ComplianceAuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * Append-only store for the LCB-FT compliance audit journal.
 *
 * Only [org.commonlink.service.ComplianceAuditLogService] should use the write-related methods
 * ([nextSequenceValue], [acquireWriteLock]) — see that class's KDoc for why it is the sole entry
 * point. `UPDATE`/`DELETE` are unavailable on this table at the database level (V51 migration).
 */
interface ComplianceAuditLogRepository : JpaRepository<ComplianceAuditLog, UUID> {

    /** Draws the next value from the dedicated `compliance_audit_log_seq` sequence. */
    @Query(value = "SELECT nextval('compliance_audit_log_seq')", nativeQuery = true)
    fun nextSequenceValue(): Long

    /**
     * Blocks until an exclusive lock on the single row of `compliance_audit_log_lock` is held,
     * released automatically at transaction end (standard `SELECT ... FOR UPDATE`). Serializes
     * concurrent writers so `prev_hash` is always computed against the true tail of the chain,
     * never a superseded row.
     *
     * Deliberately a plain row lock rather than `pg_advisory_xact_lock`: the advisory-lock
     * function is Postgres-only, and this table+lock is otherwise just as portable as any other
     * entity-mapped schema — the row lock is what lets [org.commonlink.service.ComplianceAuditLogServiceLogicTest]
     * run without a real Postgres.
     */
    @Query(value = "SELECT id FROM compliance_audit_log_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
    fun acquireWriteLock(): Number

    /** Most recent row in sequence order — the current tail of the hash chain, or null if empty. */
    fun findTopByOrderBySequenceNoDesc(): ComplianceAuditLog?

    /** Every row in chain order, oldest first — used by [ComplianceAuditLogService.verifyChain]. */
    fun findAllByOrderBySequenceNoAsc(): List<ComplianceAuditLog>

    /**
     * Compliance audit events for a given subject, filtered to a subset of event types, oldest
     * first. Used by [ComplianceAuditLogService.findFreezeScreeningHistory] to reconstruct the
     * per-subject freeze-screening trace for auditor review and the curator UI (prompt 17).
     */
    fun findBySubjectIdAndEventTypeInOrderBySequenceNoAsc(
        subjectId: UUID,
        eventTypes: Collection<String>,
    ): List<ComplianceAuditLog>

    /** The twenty most recent journal rows in reverse sequence order — used by the compliance dashboard. */
    fun findTop20ByOrderBySequenceNoDesc(): List<ComplianceAuditLog>

    /**
     * Most recent entry of one event type for one subject.
     *
     * Used by [org.commonlink.service.FreezeClearanceService] to date an `ALERT_CLOSED` decision
     * on the journal's own sequence: the clearance it grants must cover the evidence that existed
     * when the officer ruled, and nothing recorded after it.
     */
    fun findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(
        subjectId: UUID,
        eventType: String,
    ): ComplianceAuditLog?
}

package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.repository.ComplianceAuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Sole write path for the LCB-FT compliance audit journal (`compliance_audit_log`, V51
 * migration). No other code inserts into this table directly — every feature that needs to
 * prove a control happened (freeze screening, alert review, publication refusals, ...) calls
 * [append] here, so the platform has exactly one append-only, hash-chained journal instead of
 * one per feature.
 *
 * Never pass a secret, an encryption key, or a télédéclarant number in [payload] — this journal
 * has no field-level encryption and is not the place for it.
 *
 * ### Hash chain
 *
 * `row_hash` = SHA-256 over the canonical, length-prefixed concatenation of `sequence_no`,
 * `event_type`, `subject_type`, `subject_id`, `payload`, `actor_user_id`, `occurred_at` and
 * `prev_hash`, in that fixed order (see [canonicalBytes]). Each field is encoded as
 * `<utf8 byte length>:<field>` before concatenation — a plain separator such as `|` would let a
 * crafted `payload` shift field boundaries and forge a matching hash; length-prefixing makes the
 * encoding injective regardless of field content. This exact order and encoding is the contract:
 * changing either breaks reproducibility against previously written rows.
 *
 * `occurred_at` is truncated to microseconds before both hashing and persisting, because
 * `Instant.now()` carries nanosecond precision in memory but `TIMESTAMPTZ` only stores
 * microseconds — hashing the untruncated value would make every row fail [verifyChain] as soon
 * as it is read back from the database.
 *
 * ### Concurrency
 *
 * [append] takes a row lock on the single row of `compliance_audit_log_lock`
 * ([ComplianceAuditLogRepository.acquireWriteLock], a plain `SELECT ... FOR UPDATE` — portable,
 * unlike `pg_advisory_xact_lock`) before reading the current tail of the chain, so concurrent
 * writers serialize instead of racing to read the same "previous" row. The lock is taken
 * *before* [ComplianceAuditLogRepository.nextSequenceValue]: `nextval()` is not transactional and
 * never rolls back, so drawing it under the lock means a gap in `sequence_no` can only happen on
 * a rollback, not a race. Default `READ COMMITTED` isolation is required (not `REPEATABLE
 * READ`/`SERIALIZABLE`): those isolation levels snapshot at transaction start, before the lock is
 * acquired, which would make the tail read stale.
 */
@Service
class ComplianceAuditLogService(
    private val repo: ComplianceAuditLogRepository,
    private val objectMapper: ObjectMapper,
) {

    /**
     * Appends one event to the journal and returns the persisted row.
     *
     * @param payload any object Jackson can serialize; stored as JSON text.
     * @param subjectId the target business object (association, donation, campaign, alert), or
     *   null when the event has no single target.
     * @param actorUserId the acting user, or null for an automated process.
     */
    @Transactional
    fun append(
        eventType: String,
        subjectType: ComplianceAuditSubjectType,
        payload: Any,
        subjectId: UUID? = null,
        actorUserId: UUID? = null,
    ): ComplianceAuditLog {
        repo.acquireWriteLock()
        val prevHash = repo.findTopByOrderBySequenceNoDesc()?.rowHash
        val sequenceNo = repo.nextSequenceValue()
        val occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val payloadJson = objectMapper.writeValueAsString(payload)
        val rowHash = sha256Hex(
            canonicalBytes(sequenceNo, eventType, subjectType, subjectId, payloadJson, actorUserId, occurredAt, prevHash),
        )
        return repo.save(
            ComplianceAuditLog(
                sequenceNo = sequenceNo,
                eventType = eventType,
                subjectType = subjectType,
                subjectId = subjectId,
                payload = payloadJson,
                actorUserId = actorUserId,
                occurredAt = occurredAt,
                prevHash = prevHash,
                rowHash = rowHash,
            ),
        )
    }

    /**
     * Re-reads the whole chain in `sequence_no` order, recomputes every `row_hash` from each
     * row's own stored fields, and checks both that the recomputed hash matches the stored one
     * and that the stored `prev_hash` matches the actual previous row's hash.
     *
     * @return the `sequence_no` of the first row where either check fails, or null if the entire
     *   chain is intact.
     */
    @Transactional(readOnly = true)
    fun verifyChain(): Long? {
        var expectedPrevHash: String? = null
        for (row in repo.findAllByOrderBySequenceNoAsc()) {
            val recomputed = sha256Hex(
                canonicalBytes(row.sequenceNo, row.eventType, row.subjectType, row.subjectId, row.payload, row.actorUserId, row.occurredAt, row.prevHash),
            )
            if (row.prevHash != expectedPrevHash || recomputed != row.rowHash) {
                return row.sequenceNo
            }
            expectedPrevHash = row.rowHash
        }
        return null
    }

    private fun canonicalBytes(
        sequenceNo: Long,
        eventType: String,
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID?,
        payload: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        prevHash: String?,
    ): ByteArray {
        val fields = listOf(
            sequenceNo.toString(),
            eventType,
            subjectType.name,
            subjectId?.toString() ?: "",
            payload,
            actorUserId?.toString() ?: "",
            occurredAt.toString(),
            prevHash ?: "",
        )
        val builder = StringBuilder()
        for (field in fields) {
            val bytes = field.toByteArray(Charsets.UTF_8)
            builder.append(bytes.size).append(':').append(field)
        }
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

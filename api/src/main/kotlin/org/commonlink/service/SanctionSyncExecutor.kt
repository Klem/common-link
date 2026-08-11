package org.commonlink.service

import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.repository.SanctionSyncStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Transactional executor for a single scheduled sanctions-register synchronisation cycle.
 *
 * Called by [SanctionSyncScheduler] on each scheduled tick; also injectable directly for
 * testing and operational tooling.
 *
 * ### Transaction model
 *
 * [execute] runs in its own `READ COMMITTED` transaction (same isolation requirement as
 * [ComplianceAuditLogService.append]). It immediately acquires `FOR UPDATE` on the single row
 * of `sanctions_sync_state` — this lock is held for the entire duration of the method and is
 * released only when the outer transaction commits. Concurrent instances block on the lock and
 * run sequentially, so double-ingestion cannot occur.
 *
 * [SanctionIngestionRunner.run] executes in a `REQUIRES_NEW` transaction suspended from the
 * outer one. If ingestion fails, its transaction rolls back independently — the outer transaction
 * is NOT marked rollback-only and can commit the `last_attempt_at` update and any audit entries.
 *
 * ### Failure behaviour
 *
 * On ingestion failure: `last_attempt_at` is committed (the attempt is on record);
 * `last_success_at` and `last_publication_date` are left unchanged (screening continues on the
 * last known register). The failure is logged at ERROR with an actionable message and written
 * to the compliance audit journal via [ComplianceAuditLogService.appendSyncFailure].
 */
@Service
class SanctionSyncExecutor(
    private val stateRepo: SanctionSyncStateRepository,
    private val runner: SanctionIngestionRunner,
    private val sanctionedEntityRepo: SanctionedEntityRepository,
    private val auditLog: ComplianceAuditLogService,
    private val alertService: ComplianceAlertService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute() {
        stateRepo.acquireWriteLock()
        val state = stateRepo.findById(1.toShort()).orElseThrow {
            IllegalStateException("sanctions_sync_state row 1 not found — V58 migration may not have run")
        }
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        state.lastAttemptAt = now

        try {
            runner.run()
            state.lastSuccessAt = now
            state.lastPublicationDate = sanctionedEntityRepo.findMaxPublicationDate()
            stateRepo.save(state)
            log.info(
                "LCB-FT sanctions: scheduled sync complete — publication {}, last success {}",
                state.lastPublicationDate, now,
            )
        } catch (ex: Exception) {
            stateRepo.save(state) // commit lastAttemptAt; lastSuccessAt unchanged
            log.error(
                "LCB-FT sanctions: scheduled sync FAILED — screening continues on last known register " +
                    "(last success: {}). ACTION REQUIRED: verify DG Trésor registry availability and network access.",
                state.lastSuccessAt, ex,
            )
            notifySyncFailure(ex, state.lastSuccessAt)
        }
    }

    private fun notifySyncFailure(ex: Exception, lastSuccessAt: Instant?) {
        val auditEntry = auditLog.appendSyncFailure(
            reason = "${ex.javaClass.simpleName}: ${ex.message?.take(500) ?: "no message"}",
            lastSuccessAt = lastSuccessAt,
        )
        alertService.createOrIgnore(
            origin = ComplianceAlertOrigin.SYNC_FAILURE,
            subjectType = ComplianceAlertSubjectType.SYSTEM,
            subjectId = null,
            severity = ComplianceAlertSeverity.MEDIUM,
            auditLogSeqRef = auditEntry.sequenceNo,
        )
    }
}

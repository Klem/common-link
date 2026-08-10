package org.commonlink.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Isolation boundary for scheduled ingestion.
 *
 * [SanctionIngestionService.ingest] is `@Transactional(REQUIRED)` and will join the caller's
 * transaction. If it throws, Spring marks the caller's transaction rollback-only — even if the
 * caller catches the exception. This would prevent [SanctionSyncExecutor] from committing the
 * `last_attempt_at` timestamp after a failure.
 *
 * Wrapping `ingest()` here with `REQUIRES_NEW` creates an independent transaction. On failure
 * that transaction rolls back, [SanctionIngestionRunner.run] throws to the caller, but the
 * caller's transaction is **not** marked rollback-only — it can still commit its own state.
 */
@Service
class SanctionIngestionRunner(private val service: SanctionIngestionService) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun run() = service.ingest()
}

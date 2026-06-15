package org.commonlink.service

import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.repository.OnchainJobRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Transactional claim step for the on-chain outbox worker.
 *
 * Lives in its own Spring-managed bean so that the @Transactional proxy applies on the
 * external call from [OnchainJobWorker.tick] — self-invocation bypasses AOP proxies.
 * The transaction commits (status=RUNNING persisted, row-locks released) before control
 * returns to tick(), so a crash mid-dispatch leaves jobs in RUNNING (recoverable) rather
 * than silently re-PENDING where two workers could double-dispatch the same job.
 */
@Component
class OnchainJobClaimer(private val repo: OnchainJobRepository) {

    /**
     * Locks up to [batchSize] PENDING jobs via FOR UPDATE SKIP LOCKED, transitions them to
     * RUNNING, increments the attempt counter, and commits before returning.
     * Concurrent callers transparently skip locked rows.
     */
    @Transactional
    fun claimBatch(batchSize: Int): List<OnchainJob> {
        val locked = repo.lockBatch(batchSize)
        val now = Instant.now()
        locked.forEach {
            it.status = OnchainJobStatus.RUNNING
            it.updatedAt = now
            it.attempts += 1
        }
        return locked
    }
}

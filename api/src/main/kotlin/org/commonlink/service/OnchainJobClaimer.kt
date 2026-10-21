package org.commonlink.service

import org.commonlink.config.OnchainConfig
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.repository.OnchainJobRepository
import org.slf4j.LoggerFactory
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
class OnchainJobClaimer(
    private val repo: OnchainJobRepository,
    private val cfg: OnchainConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Locks up to [batchSize] claimable PENDING jobs of the current chain via FOR UPDATE SKIP LOCKED,
     * transitions them to RUNNING, increments the attempt counter, and commits before returning.
     * A job whose dependency is not DONE is not claimed and consumes no attempt; when that dependency is
     * FAILED it would wait forever, so the count of such blocked jobs is logged at WARN.
     */
    @Transactional
    fun claimBatch(batchSize: Int): List<OnchainJob> {
        val chainId = cfg.currentChainId()
        val blocked = repo.countBlockedByFailedDependency(chainId)
        if (blocked > 0) {
            logger.warn("{} on-chain job(s) on chain {} are blocked by a FAILED dependency", blocked, chainId)
        }
        val locked = repo.lockBatch(chainId, batchSize)
        val now = Instant.now()
        locked.forEach {
            it.status = OnchainJobStatus.RUNNING
            it.updatedAt = now
            it.attempts += 1
        }
        return locked
    }
}

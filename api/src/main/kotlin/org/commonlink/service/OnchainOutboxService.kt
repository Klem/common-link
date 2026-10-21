package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.config.OnchainConfig
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.repository.OnchainJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Enqueues outbox jobs for the on-chain registry worker to dispatch asynchronously.
 *
 * Every job targets the **current chain** ([OnchainConfig.currentChainId]: `0` in mock mode). Callers
 * provide a stable correlation key (e.g. `"DONATION:<uuid>"`) so the enqueue is idempotent per chain —
 * replays of the same business event do not produce duplicate jobs on that chain.
 */
@Service
class OnchainOutboxService(
    private val repo: OnchainJobRepository,
    private val objectMapper: ObjectMapper,
    private val cfg: OnchainConfig,
) {
    /**
     * @param id UUID of a job.
     * @return the job, whatever its chain, or null when unknown.
     */
    fun find(id: UUID): OnchainJob? = repo.findById(id).orElse(null)

    /**
     * @param correlationKey key of a business fact (e.g. `"DONATION:<uuid>"`).
     * @return the job recording that fact on the **current** chain, or null when there is none.
     */
    fun findCurrent(correlationKey: String): OnchainJob? =
        repo.findByChainIdAndCorrelationKey(cfg.currentChainId(), correlationKey)

    /**
     * Enqueue a job idempotently on the current chain. Returns the existing job if [correlationKey] is
     * already known on that chain (the [dependsOn] of the existing job is left untouched).
     *
     * @param action the on-chain action to perform
     * @param payload an object Jackson can serialize; will be persisted as JSONB
     * @param correlationKey opaque key uniquely identifying the source business event; `null` disables idempotency
     * @param dependsOn prerequisite job that must be `DONE` before this one is claimed; it must target the same chain
     * @throws IllegalArgumentException if [dependsOn] targets another chain
     */
    @Transactional
    fun enqueue(
        action: OnchainJobAction,
        payload: Any,
        correlationKey: String? = null,
        dependsOn: OnchainJob? = null,
    ): OnchainJob {
        val chainId = cfg.currentChainId()
        require(dependsOn == null || dependsOn.chainId == chainId) {
            "Dependency ${dependsOn?.id} targets chain ${dependsOn?.chainId}, not the current chain $chainId"
        }
        correlationKey?.let { key ->
            repo.findByChainIdAndCorrelationKey(chainId, key)?.let { return it }
        }
        return repo.save(
            OnchainJob(
                action = action,
                payloadJson = objectMapper.writeValueAsString(payload),
                correlationKey = correlationKey,
                chainId = chainId,
                dependsOnJobId = dependsOn?.id,
            ),
        )
    }
}

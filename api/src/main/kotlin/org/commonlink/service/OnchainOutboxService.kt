package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.repository.OnchainJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Enqueues outbox jobs for the on-chain registry worker to dispatch asynchronously.
 *
 * Callers should provide a stable [correlationKey] (e.g. `"DONATION:<uuid>"`) so the
 * enqueue is idempotent — replays of the same business event will not produce duplicate jobs.
 */
@Service
class OnchainOutboxService(
    private val repo: OnchainJobRepository,
    private val objectMapper: ObjectMapper,
) {
    /**
     * Enqueue a job idempotently. Returns the existing job if [correlationKey] is already known.
     *
     * @param action the on-chain action to perform
     * @param payload an object Jackson can serialize; will be persisted as JSONB
     * @param correlationKey opaque key uniquely identifying the source business event; `null` disables idempotency
     */
    fun find(id: java.util.UUID): OnchainJob? = repo.findById(id).orElse(null)

    @Transactional
    fun enqueue(action: OnchainJobAction, payload: Any, correlationKey: String? = null): OnchainJob {
        correlationKey?.let { key ->
            repo.findByCorrelationKey(key)?.let { return it }
        }
        return repo.save(
            OnchainJob(
                action = action,
                payloadJson = objectMapper.writeValueAsString(payload),
                correlationKey = correlationKey,
            ),
        )
    }
}

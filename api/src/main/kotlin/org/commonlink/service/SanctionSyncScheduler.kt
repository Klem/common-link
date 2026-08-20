package org.commonlink.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Scheduled driver for asset-freeze register synchronisation.
 *
 * Fires at a configurable interval (`commonlink.sanctions.sync.fixed-delay-ms`, default 24 h)
 * with an initial delay after startup (`commonlink.sanctions.sync.initial-delay-ms`, default 1 min).
 * `fixedDelay` semantics: the interval is measured from the end of the previous execution,
 * so a slow or failing sync never overlaps with the next attempt.
 *
 * Disabled when `commonlink.sanctions.sync.enabled=false` (test environments using fixture data).
 * The actual synchronisation logic — including the distributed lock, state update, and failure
 * handling — lives in [SanctionSyncExecutor], which is always available as a bean for direct
 * invocation in tests and operational tooling.
 *
 * [org.springframework.scheduling.annotation.EnableScheduling] is activated by
 * [org.commonlink.config.Web3jConfig].
 */
@Service
@ConditionalOnProperty(prefix = "commonlink.sanctions.sync", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SanctionSyncScheduler(private val executor: SanctionSyncExecutor) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${commonlink.sanctions.sync.fixed-delay-ms:86400000}",
        initialDelayString = "\${commonlink.sanctions.sync.initial-delay-ms:60000}",
    )
    fun tick() {
        log.debug("LCB-FT sanctions: scheduled sync tick")
        executor.execute()
    }
}

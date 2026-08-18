package org.commonlink.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Scheduled driver for proactive Mollie Connect access-token renewal.
 *
 * Fires at `app.mollie.connect.token-refresh.fixed-delay-ms` (default 15 min) after an initial
 * `…initial-delay-ms` (default 2 min). `fixedDelay` semantics: the gap is measured from the end of
 * the previous sweep, so a slow or failing sweep never overlaps the next one.
 *
 * Disabled by `app.mollie.connect.token-refresh.enabled=false`. The sweep itself lives in
 * [MollieTokenRefreshExecutor], which stays available as a bean for direct invocation in tests and
 * operational tooling even when the schedule is off.
 *
 * [org.springframework.scheduling.annotation.EnableScheduling] is activated by
 * [org.commonlink.config.Web3jConfig].
 */
@Service
@ConditionalOnProperty(
    prefix = "app.mollie.connect.token-refresh",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class MollieTokenRefreshScheduler(private val executor: MollieTokenRefreshExecutor) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${app.mollie.connect.token-refresh.fixed-delay-ms:900000}",
        initialDelayString = "\${app.mollie.connect.token-refresh.initial-delay-ms:120000}",
    )
    fun tick() {
        logger.debug("Mollie token refresh: scheduled sweep tick")
        executor.sweep()
    }
}

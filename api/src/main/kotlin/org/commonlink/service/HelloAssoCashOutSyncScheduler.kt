package org.commonlink.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Thin scheduler of [HelloAssoCashOutSyncExecutor]: a first pass `app.payments.helloasso.cashout-sync-initial-delay-ms`
 * after startup (5 minutes), then one every `cashout-sync-delay-ms` (24 hours, fixed delay between the end of a pass and
 * the start of the next), so a restart never skips a day. Disabled with `app.payments.helloasso.cashout-sync-enabled=false`
 * (test profile).
 */
@Component
@ConditionalOnProperty(
    prefix = "app.payments.helloasso",
    name = ["cashout-sync-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class HelloAssoCashOutSyncScheduler(private val executor: HelloAssoCashOutSyncExecutor) {

    /** Runs one tracking pass over every association. */
    @Scheduled(
        initialDelayString = "\${app.payments.helloasso.cashout-sync-initial-delay-ms:300000}",
        fixedDelayString = "\${app.payments.helloasso.cashout-sync-delay-ms:86400000}",
    )
    fun tick() = executor.syncAll()
}

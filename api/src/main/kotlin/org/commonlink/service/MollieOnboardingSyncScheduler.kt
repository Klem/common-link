package org.commonlink.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Scheduled driver of the Mollie onboarding status sweep ([MollieOnboardingSyncExecutor]).
 *
 * Fires every `app.mollie.connect.onboarding-sync.fixed-delay-ms` (default 1 min) after an
 * initial `…initial-delay-ms` (default 2 min). `fixedDelay` semantics: the gap is measured from the
 * end of the previous tick, so a slow tick never overlaps the next one. The tick is cheap: the
 * executor only calls Mollie for the connections whose recheck interval has elapsed.
 *
 * Unconditional bean on purpose: `app.mollie.connect.disable-onboarding-sync` is the single switch
 * (checked by the executor), so no second, possibly contradictory flag exists.
 *
 * [org.springframework.scheduling.annotation.EnableScheduling] is activated by
 * [org.commonlink.config.SchedulingConfig].
 */
@Service
class MollieOnboardingSyncScheduler(private val executor: MollieOnboardingSyncExecutor) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** One sweep tick; delegates entirely to [MollieOnboardingSyncExecutor.sweep]. */
    @Scheduled(
        fixedDelayString = "\${app.mollie.connect.onboarding-sync.fixed-delay-ms:60000}",
        initialDelayString = "\${app.mollie.connect.onboarding-sync.initial-delay-ms:120000}",
    )
    fun tick() {
        logger.debug("Mollie onboarding sync: scheduled sweep tick")
        executor.sweep()
    }
}

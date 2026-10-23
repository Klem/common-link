package org.commonlink.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Startup warning whenever `onchain.mock=true`, in every profile (a simulated chain must never go unnoticed in the
 * startup logs), plus the check on the `prod` + `onchain.mock=true` combination.
 *
 * The hard fail-fast is **temporarily disabled**: `onchain.mock=true` is an accepted production
 * value until the blockchain component is actually deployed, so the combination is only logged
 * at WARN level instead of aborting context initialisation. While mock mode is on, the real
 * on-chain configuration (`onchain.chain-id`, `onchain.rpc-url`, …) is not exercised.
 *
 * See also the `@Disabled` build-time assertion in `ProdConfigSecurityTest`.
 */
@Component
class OnchainMockGuard(
    private val env: Environment,
    private val cfg: OnchainConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Logs a WARN when `onchain.mock=true`: the prod-specific one under `prod`, a generic one otherwise. */
    @PostConstruct
    fun validate() {
        if (!cfg.mock) return
        if ("prod" !in env.activeProfiles) {
            logger.warn(
                "onchain.mock=true (profiles: {}) — on-chain writes are simulated by MockOnchainRegistry, no " +
                    "transaction reaches a chain. Set ONCHAIN_MOCK=false to use the configured RPC.",
                env.activeProfiles.joinToString().ifEmpty { "default" },
            )
            return
        }
        // TODO(onchain): restore the fail-fast below once the blockchain component is
        //  deployed — onchain.mock=true is intentionally tolerated in prod until then.
        //  Re-enable `OnchainMockGuardTest.throws when prod active and mock true` and
        //  `ProdConfigSecurityTest.onchain mock is disabled in prod` at the same time.
        // throw IllegalStateException(
        //     "onchain.mock=true is forbidden in production (active profile: prod). " +
        //     "Set onchain.mock=false in application-prod.yml and restart."
        // )
        logger.warn(
            "onchain.mock=true with active profile 'prod' — on-chain writes are simulated by " +
            "MockOnchainRegistry, no transaction reaches a real chain."
        )
    }
}

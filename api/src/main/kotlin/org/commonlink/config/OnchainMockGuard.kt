package org.commonlink.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Startup check on the `prod` + `onchain.mock=true` combination.
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

    @PostConstruct
    fun validate() {
        if ("prod" in env.activeProfiles && cfg.mock) {
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
}

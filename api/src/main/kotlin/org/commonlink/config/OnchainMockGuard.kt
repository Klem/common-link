package org.commonlink.config

import jakarta.annotation.PostConstruct
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Fail-fast guard: prevents the application from starting if the `prod` profile is active
 * while `onchain.mock=true`. Raises [IllegalStateException] during context initialisation.
 *
 * This runs in all environments (cheap no-op unless the forbidden combination is present),
 * complementing the build-time assertion in `ProdConfigSecurityTest`.
 */
@Component
class OnchainMockGuard(
    private val env: Environment,
    private val cfg: OnchainConfig,
) {
    @PostConstruct
    fun validate() {
        if ("prod" in env.activeProfiles && cfg.mock) {
            throw IllegalStateException(
                "onchain.mock=true is forbidden in production (active profile: prod). " +
                "Set onchain.mock=false in application-prod.yml and restart."
            )
        }
    }
}

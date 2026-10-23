package org.commonlink.config

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Startup fail-fast for the real (non-mock) on-chain mode, in every profile.
 *
 * When `onchain.mock=false`, the backend signs real transactions and derives public, permanent addresses,
 * so a placeholder secret or an insecure RPC endpoint must abort startup instead of being discovered on-chain:
 * - HMAC address secrets blank or still a committed `dev-placeholder…` value → refused (addresses would be
 *   derived from a public secret and could never be changed once something is recorded on-chain) — except on the
 *   local Anvil node (chain [ANVIL_CHAIN_ID] reached over a loopback RPC), whose chain is private and disposable;
 * - chain [ANVIL_CHAIN_ID] reached over a non-loopback RPC → refused (that exemption must never leave the workstation);
 * - recorder private key blank → refused;
 * - RPC URL not `https://` → refused, except a loopback `http://` (local Anvil node);
 * - registry address blank or the zero address → refused.
 * Error messages name the property, never its value. In mock mode nothing is checked (see [OnchainMockGuard]).
 */
@Component
class OnchainRealModeValidator(private val cfg: OnchainConfig) {

    /** @throws IllegalStateException listing every invalid `onchain.*` property when `onchain.mock=false`. */
    @PostConstruct
    fun validate() {
        if (cfg.mock) return
        val errors = problems(cfg)
        check(errors.isEmpty()) { "Invalid on-chain configuration for onchain.mock=false: ${errors.joinToString("; ")}" }
    }

    companion object {
        private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")

        /** Chain id of a local Anvil node (`docker compose --profile chain`, `deploy-registry.sh anvil`). */
        const val ANVIL_CHAIN_ID = 31337L

        /**
         * @param cfg configuration to check.
         * @return human-readable problems (property names only, never values); empty when valid.
         */
        fun problems(cfg: OnchainConfig): List<String> = buildList {
            val uri = runCatching { URI(cfg.rpcUrl) }.getOrNull()
            val loopback = uri?.host in LOOPBACK_HOSTS
            val localAnvil = cfg.chainId == ANVIL_CHAIN_ID && loopback

            fun secret(name: String, value: String) {
                if (value.isBlank()) add("$name is blank")
                else if (value.startsWith("dev-placeholder") && !localAnvil) add("$name is still a committed dev placeholder")
            }
            secret("onchain.donor-address-secret", cfg.donorAddressSecret)
            secret("onchain.association-address-secret", cfg.associationAddressSecret)
            if (cfg.recorderPk.isBlank()) add("onchain.recorder-pk is blank")

            val secure = uri?.scheme == "https" || (uri?.scheme == "http" && loopback)
            if (!secure) add("onchain.rpc-url must be https:// (http:// only allowed on localhost)")
            if (cfg.chainId == ANVIL_CHAIN_ID && !loopback) {
                add("onchain.chain-id is the local Anvil chain but onchain.rpc-url is not a loopback address")
            }

            if (cfg.registryAddress.isBlank() || cfg.registryAddress.equals(ZERO_ADDRESS, ignoreCase = true)) {
                add("onchain.registry-address is blank or the zero address")
            }
        }
    }
}

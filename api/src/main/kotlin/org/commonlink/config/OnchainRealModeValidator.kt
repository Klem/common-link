package org.commonlink.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
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
 * Then, once the configuration is valid, the RPC itself is probed ([probeRpc]): it must answer, be on
 * `onchain.chain-id` and hold the registry contract — otherwise startup is aborted (a dead RPC, the wrong network or
 * a local Anvil node reset without redeploying would otherwise only surface as failing jobs).
 * Error messages name the property, never its value (the RPC URL may embed a token). In mock mode nothing is checked
 * (see [OnchainMockGuard]).
 */
@Component
class OnchainRealModeValidator(
    private val cfg: OnchainConfig,
    private val web3j: ObjectProvider<Web3j>,
) {

    /**
     * @throws IllegalStateException listing every invalid `onchain.*` property, or when the RPC probe fails, when
     *   `onchain.mock=false`.
     */
    @PostConstruct
    fun validate() {
        if (cfg.mock) return
        val errors = problems(cfg)
        check(errors.isEmpty()) { "Invalid on-chain configuration for onchain.mock=false: ${errors.joinToString("; ")}" }
        val client = checkNotNull(web3j.getIfAvailable()) { "onchain.mock=false but no Web3j client is configured" }
        probeRpc(client, cfg, PROBE_ATTEMPTS, PROBE_DELAY_MS)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OnchainRealModeValidator::class.java)
        private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")
        private const val PROBE_ATTEMPTS = 3
        private const val PROBE_DELAY_MS = 2_000L

        /** Chain id of a local Anvil node (`docker compose --profile chain`, `deploy-registry.sh anvil`). */
        const val ANVIL_CHAIN_ID = 31337L

        /**
         * Probes the RPC up to [attempts] times, [delayMs] apart (a brief outage of a public RPC must not abort a
         * deployment), logging each failure at WARN.
         *
         * @param web3j client bound to `onchain.rpc-url`.
         * @param cfg configuration whose chain id and registry address are expected.
         * @param sleep pause between attempts (replaced in tests).
         * @throws IllegalStateException with the last problem when every attempt failed.
         */
        fun probeRpc(
            web3j: Web3j,
            cfg: OnchainConfig,
            attempts: Int,
            delayMs: Long,
            sleep: (Long) -> Unit = Thread::sleep,
        ) {
            var lastProblem = ""
            for (attempt in 1..attempts) {
                val problem = runCatching { rpcProblem(web3j, cfg) }
                    // Exception class only: its message may contain the RPC URL.
                    .getOrElse { "onchain.rpc-url is unreachable (${it.javaClass.simpleName})" }
                    ?: run {
                        logger.info("On-chain RPC reachable: chain {} with the registry deployed", cfg.chainId)
                        return
                    }
                lastProblem = problem
                logger.warn("On-chain RPC check {}/{} failed: {}", attempt, attempts, problem)
                if (attempt < attempts) sleep(delayMs)
            }
            throw IllegalStateException("On-chain RPC check failed after $attempts attempts: $lastProblem")
        }

        /** @return the problem found on the RPC, or null when it is on the right chain and holds the registry. */
        private fun rpcProblem(web3j: Web3j, cfg: OnchainConfig): String? {
            val chain = web3j.ethChainId().send()
            if (chain.hasError()) return "onchain.rpc-url answered eth_chainId with an error"
            if (chain.chainId.toLong() != cfg.chainId) {
                return "onchain.rpc-url is on chain ${chain.chainId}, onchain.chain-id is ${cfg.chainId}"
            }
            val code = web3j.ethGetCode(cfg.registryAddress, DefaultBlockParameterName.LATEST).send()
            if (code.hasError()) return "onchain.rpc-url answered eth_getCode with an error"
            if (code.code.isNullOrBlank() || code.code.equals("0x", ignoreCase = true)) {
                return "no contract at onchain.registry-address on chain ${cfg.chainId}"
            }
            return null
        }

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

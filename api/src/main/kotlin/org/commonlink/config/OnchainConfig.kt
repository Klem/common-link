package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `onchain.*` configuration. Secrets (recorder private key, HMAC address secrets) come from environment
 * variables and are never printed: [toString] masks them.
 */
@ConfigurationProperties(prefix = "onchain")
data class OnchainConfig(
    val rpcUrl: String,
    val chainId: Long,
    val registryAddress: String,
    val recorderPk: String,
    val pollingIntervalMs: Long,
    val receiptTimeoutMs: Long,
    val donorAddressSecret: String,
    val associationAddressSecret: String,
    val mock: Boolean = false,
    val worker: WorkerConfig,
    val explorer: ExplorerConfig = ExplorerConfig(),
) {
    /**
     * Chain the outbox currently targets: `0` (mock sentinel) when [mock] is true, [chainId] otherwise.
     * Every job is written and read for this chain only.
     */
    fun currentChainId(): Long = if (mock) 0L else chainId

    data class WorkerConfig(
        val enabled: Boolean,
        val batchSize: Int,
        val fixedDelayMs: Long,
    )

    /**
     * Public block explorer of the target chain (Blockscout), used to build deep links for users.
     *
     * @property baseUrl explorer home, e.g. `https://gnosis-chiado.blockscout.com`; blank = no links.
     */
    data class ExplorerConfig(val baseUrl: String = "")

    override fun toString(): String =
        "OnchainConfig(mock=$mock, rpcUrl=$rpcUrl, chainId=$chainId, registryAddress=$registryAddress, " +
        "recorderPk=***, pollingIntervalMs=$pollingIntervalMs, " +
        "receiptTimeoutMs=$receiptTimeoutMs, donorAddressSecret=***, associationAddressSecret=***, " +
        "worker=$worker, explorer=$explorer)"
}

package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "onchain")
@Configuration
data class OnchainConfig(
    val rpcUrl: String,
    val chainId: Long,
    val registryAddress: String,
    val recorderPk: String,
    val curatorPk: String,
    val pollingIntervalMs: Long,
    val receiptTimeoutMs: Long,
    val donorAddressSecret: String,
    val worker: WorkerConfig,
) {
    data class WorkerConfig(
        val enabled: Boolean,
        val batchSize: Int,
        val fixedDelayMs: Long,
    )
}

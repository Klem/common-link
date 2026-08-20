package org.commonlink.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.tx.gas.DefaultGasProvider

/**
 * web3j wiring for the real chain: RPC client, signing credentials and transaction managers.
 *
 * Inactive when `onchain.mock=true` — the same condition (inverted) that gates
 * [org.commonlink.onchain.OnchainRegistryClient], its only consumer. Without this gate the
 * credential beans would be built from the empty `onchain.recorder-pk` / `onchain.curator-pk`
 * defaults and abort startup with `Zero length BigInteger`.
 *
 * `@EnableScheduling` used to live here; it now sits in [SchedulingConfig] so scheduling survives
 * mock mode.
 */
@Configuration
@ConditionalOnProperty(prefix = "onchain", name = ["mock"], havingValue = "false", matchIfMissing = true)
class Web3jConfig(private val cfg: OnchainConfig) {

    @Bean fun web3j(): Web3j =
        Web3j.build(HttpService(cfg.rpcUrl))

    /** Signer for RECORDER_ROLE (donations, campaigns, milestones). */
    @Bean("recorderCredentials")
    fun recorderCredentials(): Credentials = Credentials.create(cfg.recorderPk)

    /** Signer for CURATOR_ROLE (verify / revoke / status transitions). */
    @Bean("curatorCredentials")
    fun curatorCredentials(): Credentials = Credentials.create(cfg.curatorPk)

    /** Conservative gas provider — replace with EIP-1559 estimator before mainnet. */
    @Bean fun gasProvider(): ContractGasProvider = DefaultGasProvider()

    @Bean("recorderTxManager")
    fun recorderTxManager(web3j: Web3j, @Qualifier("recorderCredentials") c: Credentials): TransactionManager =
        RawTransactionManager(web3j, c, cfg.chainId, /*attempts*/ 40, cfg.pollingIntervalMs)

    @Bean("curatorTxManager")
    fun curatorTxManager(web3j: Web3j, @Qualifier("curatorCredentials") c: Credentials): TransactionManager =
        RawTransactionManager(web3j, c, cfg.chainId, /*attempts*/ 40, cfg.pollingIntervalMs)
}

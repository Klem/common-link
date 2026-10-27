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
 * credential bean would be built from the empty `onchain.recorder-pk` default and abort startup with
 * `Zero length BigInteger`. Two signers, each with its own transaction manager and therefore its own nonce stream:
 * the recorder (every record) and the dedicated pauser (emergency `pause()` only, decision 31).
 *
 * `@EnableScheduling` used to live here; it now sits in [SchedulingConfig] so scheduling survives
 * mock mode.
 */
@Configuration
@ConditionalOnProperty(prefix = "onchain", name = ["mock"], havingValue = "false", matchIfMissing = true)
class Web3jConfig(private val cfg: OnchainConfig) {

    @Bean fun web3j(): Web3j =
        Web3j.build(HttpService(cfg.rpcUrl))

    /** Signer for RECORDER_ROLE — the single write role of the registry (registrations, budget, donations, payouts). */
    @Bean("recorderCredentials")
    fun recorderCredentials(): Credentials = Credentials.create(cfg.recorderPk)

    /** Conservative gas provider — replace with EIP-1559 estimator before mainnet. */
    @Bean fun gasProvider(): ContractGasProvider = DefaultGasProvider()

    @Bean("recorderTxManager")
    fun recorderTxManager(web3j: Web3j, @Qualifier("recorderCredentials") c: Credentials): TransactionManager =
        RawTransactionManager(web3j, c, cfg.chainId, /*attempts*/ 40, cfg.pollingIntervalMs)

    /** Signer for PAUSER_ROLE — a dedicated key that can only freeze the registry (decision 31). */
    @Bean("pauserCredentials")
    fun pauserCredentials(): Credentials = Credentials.create(cfg.pauserPk)

    /**
     * Transaction manager of the pauser: separate from [recorderTxManager] so an emergency pause never waits behind,
     * nor collides with, the recorder's nonce stream.
     */
    @Bean("pauserTxManager")
    fun pauserTxManager(web3j: Web3j, @Qualifier("pauserCredentials") c: Credentials): TransactionManager =
        RawTransactionManager(web3j, c, cfg.chainId, /*attempts*/ 40, cfg.pollingIntervalMs)
}

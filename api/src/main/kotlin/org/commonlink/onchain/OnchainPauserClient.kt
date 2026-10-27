package org.commonlink.onchain

import org.commonlink.config.OnchainConfig
import org.commonlink.onchain.generated.CommonLinkRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.ContractGasProvider

/**
 * Real [OnchainPauser]: `pause()` / `unpause()` signed by the dedicated pauser key (`pauserTxManager`, own nonce stream),
 * sent directly — never through the outbox, so a stuck worker can never delay a freeze or a resume.
 */
@Service
@ConditionalOnProperty(prefix = "onchain", name = ["mock"], havingValue = "false", matchIfMissing = true)
class OnchainPauserClient(
    private val web3j: Web3j,
    private val cfg: OnchainConfig,
    @Qualifier("pauserTxManager") private val pauserTxMgr: TransactionManager,
    @Qualifier("pauserCredentials") private val pauserCredentials: Credentials,
    private val gas: ContractGasProvider,
) : OnchainPauser {

    private val pauserContract: CommonLinkRegistry by lazy {
        CommonLinkRegistry.load(cfg.registryAddress, web3j, pauserTxMgr, gas)
    }

    /** Sends `pause()` signed by the pauser key and waits for the receipt (polling bounded by the tx manager). */
    override fun pause(): TransactionReceipt = pauserContract.pause().send()

    /** Sends `unpause()` signed by the pauser key and waits for the receipt (polling bounded by the tx manager). */
    override fun unpause(): TransactionReceipt = pauserContract.unpause().send()

    /** Reads `hasRole(UNPAUSER_ROLE, pauser)` with an `eth_call` (no transaction). */
    override fun canUnpause(): Boolean = pauserContract.hasRole(UNPAUSER_ROLE, pauserCredentials.address).send()

    private companion object {
        val UNPAUSER_ROLE: ByteArray = Hash.sha3("UNPAUSER_ROLE".toByteArray())
    }
}

package org.commonlink.onchain

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Development-only double of [OnchainRegistryClient].
 *
 * Active when `onchain.mock=true` (set in `application.yml` for local dev). Logs every call at
 * INFO level and returns a synthetic [TransactionReceipt] — random tx hash, auto-incremented block
 * number, status `0x1`. No web3j RPC calls, no private keys, no network I/O. Nothing is written on
 * any chain.
 *
 * **Temporarily tolerated in production** until the blockchain component is deployed:
 * [org.commonlink.config.OnchainMockGuard] only logs a WARN when profile `prod` is active with
 * `onchain.mock=true` — its fail-fast is commented out (see the TODO there).
 */
@Component
@ConditionalOnProperty(prefix = "onchain", name = ["mock"], havingValue = "true")
class MockOnchainRegistry : OnchainRegistry {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val blockCounter = AtomicLong(1)

    private fun syntheticReceipt(label: String): TransactionReceipt {
        val receipt = TransactionReceipt()
        receipt.transactionHash = "0x" + Random.nextBytes(32).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        receipt.setBlockNumber("0x${blockCounter.getAndIncrement().toString(16)}")
        receipt.status = "0x1"
        logger.info("Mock onchain [{}] txHash={} blockHeight={} status={}", label, receipt.transactionHash, receipt.blockNumber, receipt.status)
        return receipt
    }

    private fun ByteArray.shortHex() = take(4).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /** Returns a synthetic receipt; nothing is sent on-chain (mock mode). */
    override fun registerAssociation(address: String, sirenHash: ByteArray) =
        syntheticReceipt("registerAssociation address=$address")

    /** Returns a synthetic receipt; nothing is sent on-chain (mock mode). */
    override fun registerCampaign(id: ByteArray, association: String, budgetHash: ByteArray) =
        syntheticReceipt("registerCampaign id=${id.shortHex()} association=$association")

    /** Returns a synthetic receipt; nothing is sent on-chain (mock mode). */
    override fun updateCampaignBudget(id: ByteArray, newBudgetHash: ByteArray) =
        syntheticReceipt("updateCampaignBudget id=${id.shortHex()}")

    /** Returns a synthetic receipt; nothing is sent on-chain (mock mode). */
    override fun recordDonation(
        donationId: ByteArray,
        donor: String,
        campaignId: ByteArray,
        amountCents: BigInteger,
        receiptHash: ByteArray,
        txRef: ByteArray,
    ) = syntheticReceipt("recordDonation donor=$donor amountCents=$amountCents")

    /** Returns a synthetic receipt; nothing is sent on-chain (mock mode). */
    override fun recordPayout(payoutId: ByteArray, campaignId: ByteArray, amountCents: BigInteger, txRef: ByteArray) =
        syntheticReceipt("recordPayout amountCents=$amountCents")

    /** Nothing is ever registered on the mock chain: every registration job is sent. */
    override fun registeredAssociationSirenHash(address: String): String? = null

    /** Nothing is ever registered on the mock chain: every registration job is sent. */
    override fun registeredCampaign(id: ByteArray): RegisteredCampaign? = null
}

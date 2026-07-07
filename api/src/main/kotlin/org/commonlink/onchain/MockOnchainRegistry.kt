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
 * number, status `0x1`. No web3j RPC calls, no private keys, no network I/O.
 *
 * **NEVER active in production.** [org.commonlink.config.OnchainMockGuard] enforces this at
 * startup: the context refuses to start if profile `prod` is active with `onchain.mock=true`.
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

    override fun verifyAssociation(address: String, sirenHash: ByteArray) =
        syntheticReceipt("verifyAssociation address=$address")

    override fun revokeAssociation(address: String) =
        syntheticReceipt("revokeAssociation address=$address")

    override fun restoreAssociation(address: String) =
        syntheticReceipt("restoreAssociation address=$address")

    override fun createCampaign(
        id: ByteArray,
        association: String,
        goalCents: BigInteger,
        milestoneCount: BigInteger,
        budgetHash: ByteArray,
    ) = syntheticReceipt("createCampaign id=${id.shortHex()} association=$association goalCents=$goalCents")

    override fun publishCampaign(id: ByteArray) =
        syntheticReceipt("publishCampaign id=${id.shortHex()}")

    override fun updateCampaignBudget(id: ByteArray, newBudgetHash: ByteArray) =
        syntheticReceipt("updateCampaignBudget id=${id.shortHex()}")

    override fun pauseCampaign(id: ByteArray) =
        syntheticReceipt("pauseCampaign id=${id.shortHex()}")

    override fun unpauseCampaign(id: ByteArray) =
        syntheticReceipt("unpauseCampaign id=${id.shortHex()}")

    override fun cancelCampaign(id: ByteArray) =
        syntheticReceipt("cancelCampaign id=${id.shortHex()}")

    override fun completeCampaign(id: ByteArray) =
        syntheticReceipt("completeCampaign id=${id.shortHex()}")

    override fun revertCampaignToDraft(id: ByteArray) =
        syntheticReceipt("revertCampaignToDraft id=${id.shortHex()}")

    override fun recordDonation(
        donationId: ByteArray,
        donor: String,
        campaignId: ByteArray,
        amountCents: BigInteger,
        receiptHash: ByteArray,
        txRef: ByteArray,
    ) = syntheticReceipt("recordDonation donor=$donor amountCents=$amountCents")

    override fun markMilestoneReached(campaignId: ByteArray, index: BigInteger, proofHash: ByteArray) =
        syntheticReceipt("markMilestoneReached campaignId=${campaignId.shortHex()} index=$index")

    override fun recordPayout(payoutId: ByteArray, campaignId: ByteArray, amountCents: BigInteger) =
        syntheticReceipt("recordPayout amountCents=$amountCents")
}

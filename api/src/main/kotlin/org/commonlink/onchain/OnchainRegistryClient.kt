package org.commonlink.onchain

import org.commonlink.config.OnchainConfig
import org.commonlink.onchain.generated.CommonLinkRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.ReadonlyTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

@Service
class OnchainRegistryClient(
    private val web3j: Web3j,
    private val cfg: OnchainConfig,
    @Qualifier("recorderTxManager") private val recorderTxMgr: TransactionManager,
    @Qualifier("curatorTxManager") private val curatorTxMgr: TransactionManager,
    private val gas: ContractGasProvider,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val recorderContract: CommonLinkRegistry by lazy {
        CommonLinkRegistry.load(cfg.registryAddress, web3j, recorderTxMgr, gas)
    }
    private val curatorContract: CommonLinkRegistry by lazy {
        CommonLinkRegistry.load(cfg.registryAddress, web3j, curatorTxMgr, gas)
    }

    /** Read-only contract bound to no signer; never sends transactions. */
    private val viewContract: CommonLinkRegistry by lazy {
        CommonLinkRegistry.load(cfg.registryAddress, web3j, ReadonlyTransactionManager(web3j, "0x0"), gas)
    }

    // ───── Curator writes ───────────────────────────────────────────
    fun verifyAssociation(address: String, sirenHash: ByteArray): TransactionReceipt =
        curatorContract.verifyAssociation(address, sirenHash).send()

    fun revokeAssociation(address: String): TransactionReceipt =
        curatorContract.revokeAssociation(address).send()

    fun restoreAssociation(address: String): TransactionReceipt =
        curatorContract.restoreAssociation(address).send()

    fun pauseCampaign(id: ByteArray): TransactionReceipt = curatorContract.pauseCampaign(id).send()
    fun unpauseCampaign(id: ByteArray): TransactionReceipt = curatorContract.unpauseCampaign(id).send()
    fun cancelCampaign(id: ByteArray): TransactionReceipt = curatorContract.cancelCampaign(id).send()
    fun completeCampaign(id: ByteArray): TransactionReceipt = curatorContract.completeCampaign(id).send()
    fun revertCampaignToDraft(id: ByteArray): TransactionReceipt = curatorContract.revertCampaignToDraft(id).send()

    // ───── Recorder writes ──────────────────────────────────────────
    // v1.3: createCampaign no longer takes startDate/endDate — the chain sets startDate = block.timestamp
    // itself, and endDate is set only at terminal transitions. The campaign lands in Draft state.
    fun createCampaign(
        id: ByteArray,
        association: String,
        goalCents: BigInteger,
        milestoneCount: BigInteger,
        budgetHash: ByteArray,
    ): TransactionReceipt =
        recorderContract.createCampaign(id, association, goalCents, milestoneCount, budgetHash).send()

    /** Draft → Active transition. Required after createCampaign to make a campaign donation-eligible. */
    fun publishCampaign(id: ByteArray): TransactionReceipt = recorderContract.publishCampaign(id).send()

    /** Mutates budgetHash on a non-terminal campaign. Reverts with BudgetHashUnchanged on identical hash. */
    fun updateCampaignBudget(id: ByteArray, newBudgetHash: ByteArray): TransactionReceipt =
        recorderContract.updateCampaignBudget(id, newBudgetHash).send()

    fun recordDonation(
        donationId: ByteArray,
        donor: String,
        campaignId: ByteArray,
        amountCents: BigInteger,
        receiptHash: ByteArray,
        txRef: ByteArray,
    ): TransactionReceipt =
        recorderContract.recordDonation(donationId, donor, campaignId, amountCents, receiptHash, txRef).send()

    fun markMilestoneReached(campaignId: ByteArray, index: BigInteger, proofHash: ByteArray): TransactionReceipt =
        recorderContract.markMilestoneReached(campaignId, index, proofHash).send()

    /**
     * Records a confirmed payout on-chain.
     *
     * TODO: wire to recorderContract.recordPayout() once the Solidity function is deployed.
     * Parameters match the expected contract ABI: payoutId (bytes32), campaignId (bytes32), amountCents (uint256).
     */
    fun recordPayout(payoutId: ByteArray, campaignId: ByteArray, amountCents: BigInteger): TransactionReceipt =
        throw NotImplementedError("recordPayout: Solidity function not yet deployed — update recorderContract binding and remove this stub")

    // ───── Reads (no signer needed) ─────────────────────────────────
    fun getAssociation(address: String): CommonLinkRegistry.Association = viewContract.getAssociation(address).send()
    fun getCampaign(id: ByteArray): CommonLinkRegistry.Campaign = viewContract.getCampaign(id).send()
    fun getDonation(id: ByteArray): CommonLinkRegistry.Donation = viewContract.getDonation(id).send()
    fun getDonorStats(donor: String): CommonLinkRegistry.DonorStats = viewContract.getDonorStats(donor).send()
}

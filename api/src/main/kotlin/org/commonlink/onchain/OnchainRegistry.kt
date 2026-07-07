package org.commonlink.onchain

import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigInteger

/**
 * Abstraction over all write operations sent to the on-chain registry.
 *
 * Two implementations:
 * - [OnchainRegistryClient] — real web3j-backed client, active when `onchain.mock=false` (default).
 * - [MockOnchainRegistry]   — synthetic receipts, zero network I/O, active when `onchain.mock=true`.
 *
 * [org.commonlink.service.OnchainJobWorker] depends exclusively on this interface — never on a
 * concrete implementation — so the swap is transparent to the worker.
 */
interface OnchainRegistry {

    // ── Association writes ───────────────────────────────────────────────────
    fun verifyAssociation(address: String, sirenHash: ByteArray): TransactionReceipt
    fun revokeAssociation(address: String): TransactionReceipt
    fun restoreAssociation(address: String): TransactionReceipt

    // ── Campaign writes ──────────────────────────────────────────────────────
    fun createCampaign(
        id: ByteArray,
        association: String,
        goalCents: BigInteger,
        milestoneCount: BigInteger,
        budgetHash: ByteArray,
    ): TransactionReceipt

    fun publishCampaign(id: ByteArray): TransactionReceipt
    fun updateCampaignBudget(id: ByteArray, newBudgetHash: ByteArray): TransactionReceipt
    fun pauseCampaign(id: ByteArray): TransactionReceipt
    fun unpauseCampaign(id: ByteArray): TransactionReceipt
    fun cancelCampaign(id: ByteArray): TransactionReceipt
    fun completeCampaign(id: ByteArray): TransactionReceipt
    fun revertCampaignToDraft(id: ByteArray): TransactionReceipt

    // ── Donation / payout writes ─────────────────────────────────────────────
    fun recordDonation(
        donationId: ByteArray,
        donor: String,
        campaignId: ByteArray,
        amountCents: BigInteger,
        receiptHash: ByteArray,
        txRef: ByteArray,
    ): TransactionReceipt

    fun markMilestoneReached(campaignId: ByteArray, index: BigInteger, proofHash: ByteArray): TransactionReceipt

    /** Stub — Solidity `recordPayout` not yet deployed. Dispatched by [org.commonlink.entity.OnchainJobAction.RECORD_PAYOUT]. */
    fun recordPayout(payoutId: ByteArray, campaignId: ByteArray, amountCents: BigInteger): TransactionReceipt
}

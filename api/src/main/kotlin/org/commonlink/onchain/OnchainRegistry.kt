package org.commonlink.onchain

import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigInteger

/**
 * Abstraction over all write operations sent to the on-chain registry (`CommonLinkRegistry` v2).
 *
 * The registry is a notary that records facts the backend already decided, and it has no lifecycle:
 * an association and a campaign are each registered once and forever. Making an entity read-only is
 * a backend decision that never reaches the chain.
 *
 * Two implementations:
 * - [OnchainRegistryClient] — real web3j-backed client, active when `onchain.mock=false` (default).
 * - [MockOnchainRegistry]   — synthetic receipts, zero network I/O, active when `onchain.mock=true`.
 *
 * [org.commonlink.service.OnchainJobWorker] depends exclusively on this interface, so the swap is
 * transparent to the worker.
 */
interface OnchainRegistry {

    /**
     * Registers an association once and forever (called when it becomes able to collect).
     *
     * @param address pseudonymous association address
     * @param sirenHash keccak256 of the canonical SIREN (see [OnchainCodec.keccakSiren])
     */
    fun registerAssociation(address: String, sirenHash: ByteArray): TransactionReceipt

    /**
     * Registers a campaign once and forever, with its budget hash (called at its first LIVE).
     *
     * @param id campaign UUID encoded as bytes32
     * @param association pseudonymous address of the owning association, which must already be registered
     * @param budgetHash keccak256 of the canonical budget JSON
     */
    fun registerCampaign(id: ByteArray, association: String, budgetHash: ByteArray): TransactionReceipt

    /**
     * Declares a new budget hash for a registered campaign.
     *
     * @param id campaign UUID encoded as bytes32
     * @param newBudgetHash keccak256 of the canonical budget JSON
     */
    fun updateCampaignBudget(id: ByteArray, newBudgetHash: ByteArray): TransactionReceipt

    /**
     * Records a confirmed donation on a registered campaign.
     *
     * @param donationId donation UUID encoded as bytes32
     * @param donor pseudonymous donor address
     * @param campaignId campaign UUID encoded as bytes32
     * @param amountCents amount in EUR cents (Solidity `uint96`)
     * @param receiptHash hash of the fiscal receipt
     * @param txRef payment provider reference encoded as bytes32
     */
    fun recordDonation(
        donationId: ByteArray,
        donor: String,
        campaignId: ByteArray,
        amountCents: BigInteger,
        receiptHash: ByteArray,
        txRef: ByteArray,
    ): TransactionReceipt

    /**
     * Records a settled payout (disbursement of campaign funds) on-chain. Proof only: no funds move.
     * Dispatched by [org.commonlink.entity.OnchainJobAction.RECORD_PAYOUT].
     *
     * @param payoutId payout UUID encoded as bytes32 (see [OnchainCodec.uuidToBytes32])
     * @param campaignId campaign UUID encoded as bytes32; the campaign must be registered on-chain
     * @param amountCents amount in EUR cents (Solidity `uint96`)
     * @param txRef bank transfer reference (Bridge transaction id) encoded as bytes32 (see [OnchainCodec.stringToBytes32])
     */
    fun recordPayout(payoutId: ByteArray, campaignId: ByteArray, amountCents: BigInteger, txRef: ByteArray): TransactionReceipt

    /**
     * Reads the on-chain registration of an association (no transaction).
     *
     * @param address pseudonymous association address
     * @return the registered SIREN hash as lowercase `0x` hex, or null when the association was never registered
     */
    fun registeredAssociationSirenHash(address: String): String?

    /**
     * Reads the on-chain registration of a campaign (no transaction).
     *
     * @param id campaign UUID encoded as bytes32
     * @return the registration, or null when the campaign was never registered
     */
    fun registeredCampaign(id: ByteArray): RegisteredCampaign?
}

/**
 * A campaign registration as read from the chain.
 *
 * @property association owning association address (checksummed or not: compare case-insensitively)
 * @property budgetHashHex last declared budget hash as lowercase `0x` hex
 */
data class RegisteredCampaign(val association: String, val budgetHashHex: String)

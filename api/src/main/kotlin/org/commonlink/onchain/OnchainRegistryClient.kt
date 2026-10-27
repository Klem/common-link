package org.commonlink.onchain

import org.commonlink.config.OnchainConfig
import org.commonlink.onchain.generated.CommonLinkRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.ReadonlyTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Real web3j-backed [OnchainRegistry] for the `CommonLinkRegistry` hub (v3).
 *
 * Every record is signed by the recorder hot wallet (`RECORDER_ROLE`); the emergency pause is signed by another
 * key, see [OnchainPauserClient]. The registry only checks its own integrity (role, pause, duplicate ids, referential existence,
 * input sanity) — every business rule has already been applied by the backend before the job was enqueued.
 */
@Service
@ConditionalOnProperty(prefix = "onchain", name = ["mock"], havingValue = "false", matchIfMissing = true)
class OnchainRegistryClient(
    private val web3j: Web3j,
    private val cfg: OnchainConfig,
    @Qualifier("recorderTxManager") private val recorderTxMgr: TransactionManager,
    private val gas: ContractGasProvider,
) : OnchainRegistry {

    private val recorderContract: CommonLinkRegistry by lazy {
        CommonLinkRegistry.load(cfg.registryAddress, web3j, recorderTxMgr, gas)
    }

    /**
     * Read-only contract bound to no signer; never sends transactions. Its `eth_call`s carry the zero address as
     * `from`: it must be a full 20-byte address — nodes reject a short `0x0` ("odd number of digits").
     */
    private val viewContract: CommonLinkRegistry by lazy {
        CommonLinkRegistry.load(cfg.registryAddress, web3j, ReadonlyTransactionManager(web3j, ZERO_ADDRESS), gas)
    }

    // ───── Writes (recorder) ─────────────────────────────────────────

    /** Registers the association once and forever. Reverts if it (or its SIREN) is already registered. */
    override fun registerAssociation(address: String, sirenHash: ByteArray): TransactionReceipt =
        recorderContract.registerAssociation(address, sirenHash).send()

    /** Registers the campaign once and forever. Reverts if already registered or if the association is not. */
    override fun registerCampaign(id: ByteArray, association: String, budgetHash: ByteArray): TransactionReceipt =
        recorderContract.registerCampaign(id, association, budgetHash).send()

    /** Declares a new budget hash for a registered campaign (history lives in the events). */
    override fun updateCampaignBudget(id: ByteArray, newBudgetHash: ByteArray): TransactionReceipt =
        recorderContract.updateCampaignBudget(id, newBudgetHash).send()

    /** Records a confirmed donation; the campaign must be registered and the donation id unused. */
    override fun recordDonation(
        donationId: ByteArray,
        donor: String,
        campaignId: ByteArray,
        amountCents: BigInteger,
        receiptHash: ByteArray,
        txRef: ByteArray,
    ): TransactionReceipt =
        recorderContract.recordDonation(donationId, donor, campaignId, amountCents, receiptHash, txRef).send()

    /**
     * Records a settled payout on-chain (`recordPayout(bytes32,bytes32,uint96,bytes32)`): the registry stores the
     * payout record and updates the campaign statistics.
     *
     * The registry checks only that the campaign is registered (`CampaignNotRegistered`), that the `payoutId`
     * is unused (`PayoutAlreadyRecorded`), that the amount and the transfer reference are non-zero; the available
     * balance and payout eligibility are backend rules already enforced before the job was enqueued.
     *
     * @param amountCents amount in EUR cents, validated against the `uint96` range before sending
     * @param txRef bank transfer reference (Bridge transaction id) encoded as bytes32
     * @throws IllegalArgumentException when [amountCents] is out of `uint96` range. This failure is permanent, but
     *   the worker does not yet distinguish permanent from transient errors (sprint B7.1), so the job is retried
     *   until its attempts are exhausted. The guard lives here and never in the payout confirmation path: a payout
     *   already settled by the bank must stay confirmed.
     */
    override fun recordPayout(payoutId: ByteArray, campaignId: ByteArray, amountCents: BigInteger, txRef: ByteArray): TransactionReceipt =
        recorderContract.recordPayout(payoutId, campaignId, OnchainCodec.requireUint96(amountCents), txRef).send()

    // ───── Reads (no signer needed) ─────────────────────────────────

    /** Reads `getAssociation`: the SIREN hash, or null when `registeredAt == 0` (never registered). */
    override fun registeredAssociationSirenHash(address: String): String? =
        getAssociation(address).takeIf { it.registeredAt.signum() != 0 }
            ?.let { Numeric.toHexString(it.sirenHash).lowercase() }

    /** Reads `getCampaign`: the registration, or null when its association is the zero address (never registered). */
    override fun registeredCampaign(id: ByteArray): RegisteredCampaign? =
        getCampaign(id).takeIf { Numeric.toBigInt(it.association).signum() != 0 }
            ?.let { RegisteredCampaign(it.association, Numeric.toHexString(it.budgetHash).lowercase()) }

    /** Reads `paused()` on the registry. */
    override fun isPaused(): Boolean = viewContract.paused().send()

    /** Registration record of an association (`registeredAt == 0` when never registered). */
    fun getAssociation(address: String): CommonLinkRegistry.Association = viewContract.getAssociation(address).send()

    /** Registration record of a campaign (`association` is the zero address when never registered). */
    fun getCampaign(id: ByteArray): CommonLinkRegistry.Campaign = viewContract.getCampaign(id).send()

    private companion object {
        const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
    }

    /** A recorded donation (`donor` is the zero address when unknown). */
    fun getDonation(id: ByteArray): CommonLinkRegistry.Donation = viewContract.getDonation(id).send()

    /** Cumulative statistics of a donor address. */
    fun getDonorStats(donor: String): CommonLinkRegistry.DonorStats = viewContract.getDonorStats(donor).send()
}

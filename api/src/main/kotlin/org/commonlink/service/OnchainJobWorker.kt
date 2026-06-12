package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.config.OnchainConfig
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.onchain.OnchainCodec
import org.commonlink.onchain.OnchainRegistryClient
import org.commonlink.repository.CampaignRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

/**
 * Scheduled drainer for the [OnchainJob] outbox.
 *
 * Disabled by default; enabled per-profile via `onchain.worker.enabled=true`.
 * Each tick locks up to `onchain.worker.batch-size` `PENDING` jobs (`FOR UPDATE SKIP LOCKED`),
 * dispatches them to [OnchainRegistryClient], and persists the resulting tx hash/status.
 * On failure, jobs are re-queued until the 5th attempt, after which they become `FAILED`.
 */
@Service
@ConditionalOnProperty(prefix = "onchain.worker", name = ["enabled"], havingValue = "true")
class OnchainJobWorker(
    private val repo: org.commonlink.repository.OnchainJobRepository,
    private val campaignRepository: CampaignRepository,
    private val client: OnchainRegistryClient,
    private val objectMapper: ObjectMapper,
    private val cfg: OnchainConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${onchain.worker.fixed-delay-ms}")
    fun tick() {
        val batch = pickBatch()
        if (batch.isEmpty()) return
        batch.forEach(::process)
    }

    @Transactional
    protected fun pickBatch(): List<OnchainJob> {
        val locked = repo.lockBatch(cfg.worker.batchSize)
        val now = Instant.now()
        locked.forEach {
            it.status = OnchainJobStatus.RUNNING
            it.updatedAt = now
            it.attempts += 1
        }
        return locked
    }

    private fun process(job: OnchainJob) {
        try {
            val receipt = dispatch(job)
            job.txHash = receipt.transactionHash
            job.blockNumber = receipt.blockNumber.toLong()
            job.status = OnchainJobStatus.DONE
            onSuccess(job)
        } catch (ex: Exception) {
            logger.error("Onchain job {} ({}) failed on attempt {}", job.id, job.action, job.attempts, ex)
            job.lastError = ex.message?.take(2000)
            job.status = if (job.attempts >= 5) OnchainJobStatus.FAILED else OnchainJobStatus.PENDING
        } finally {
            job.updatedAt = Instant.now()
            repo.save(job)
        }
    }

    private fun onSuccess(job: OnchainJob) {
        when (job.action) {
            OnchainJobAction.REVERT_CAMPAIGN_TO_DRAFT -> {
                val campaignId = objectMapper.readValue(job.payloadJson, CampaignIdPayload::class.java).campaignId
                campaignRepository.findById(campaignId).ifPresent { campaign ->
                    campaign.status = CampaignStatus.DRAFT
                    campaignRepository.save(campaign)
                    logger.info("Campaign reverted to DRAFT after on-chain confirmation: campaignId={}", campaignId)
                }
            }
            else -> Unit
        }
    }

    private fun dispatch(job: OnchainJob): TransactionReceipt {
        return when (job.action) {
            OnchainJobAction.VERIFY_ASSOCIATION -> {
                val p = objectMapper.readValue(job.payloadJson, VerifyAssociationPayload::class.java)
                client.verifyAssociation(p.address, Numeric.hexStringToByteArray(p.sirenHashHex))
            }
            OnchainJobAction.REVOKE_ASSOCIATION -> {
                val p = objectMapper.readValue(job.payloadJson, AddressOnlyPayload::class.java)
                client.revokeAssociation(p.address)
            }
            OnchainJobAction.RESTORE_ASSOCIATION -> {
                val p = objectMapper.readValue(job.payloadJson, AddressOnlyPayload::class.java)
                client.restoreAssociation(p.address)
            }
            OnchainJobAction.CREATE_CAMPAIGN -> {
                val p = objectMapper.readValue(job.payloadJson, CreateCampaignPayload::class.java)
                // v1.3: no startDate/endDate — the chain stamps startDate itself; endDate is set
                // at terminal transition. The campaign lands in Draft and needs PUBLISH_CAMPAIGN.
                client.createCampaign(
                    OnchainCodec.uuidToBytes32(p.campaignId),
                    p.association,
                    p.goalCents,
                    BigInteger.valueOf(p.milestoneCount.toLong()),
                    Numeric.hexStringToByteArray(p.budgetHashHex),
                )
            }
            OnchainJobAction.UPDATE_CAMPAIGN_BUDGET -> {
                val p = objectMapper.readValue(job.payloadJson, UpdateCampaignBudgetPayload::class.java)
                client.updateCampaignBudget(
                    OnchainCodec.uuidToBytes32(p.campaignId),
                    Numeric.hexStringToByteArray(p.newBudgetHashHex),
                )
            }
            OnchainJobAction.RECORD_DONATION -> {
                val p = objectMapper.readValue(job.payloadJson, RecordDonationPayload::class.java)
                client.recordDonation(
                    OnchainCodec.uuidToBytes32(p.donationId),
                    p.donor,
                    OnchainCodec.uuidToBytes32(p.campaignId),
                    p.amountCents,
                    Numeric.hexStringToByteArray(p.receiptHashHex),
                    OnchainCodec.stringToBytes32(p.txRef),
                )
            }
            OnchainJobAction.MARK_MILESTONE_REACHED -> {
                val p = objectMapper.readValue(job.payloadJson, MilestonePayload::class.java)
                client.markMilestoneReached(
                    OnchainCodec.uuidToBytes32(p.campaignId),
                    BigInteger.valueOf(p.index.toLong()),
                    Numeric.hexStringToByteArray(p.proofHashHex),
                )
            }
            OnchainJobAction.PUBLISH_CAMPAIGN         -> client.publishCampaign(byPayloadCampaignId(job))
            OnchainJobAction.REVERT_CAMPAIGN_TO_DRAFT -> client.revertCampaignToDraft(byPayloadCampaignId(job))
            OnchainJobAction.PAUSE_CAMPAIGN           -> client.pauseCampaign(byPayloadCampaignId(job))
            OnchainJobAction.UNPAUSE_CAMPAIGN         -> client.unpauseCampaign(byPayloadCampaignId(job))
            OnchainJobAction.CANCEL_CAMPAIGN          -> client.cancelCampaign(byPayloadCampaignId(job))
            OnchainJobAction.COMPLETE_CAMPAIGN        -> client.completeCampaign(byPayloadCampaignId(job))
        }
    }

    private fun byPayloadCampaignId(job: OnchainJob): ByteArray {
        val p = objectMapper.readValue(job.payloadJson, CampaignIdPayload::class.java)
        return OnchainCodec.uuidToBytes32(p.campaignId)
    }
}

// ────── Payload DTOs ──────
data class VerifyAssociationPayload(val address: String, val sirenHashHex: String)
data class AddressOnlyPayload(val address: String)
data class CampaignIdPayload(val campaignId: UUID)
data class CreateCampaignPayload(
    val campaignId: UUID,
    val association: String,
    val goalCents: BigInteger,
    val milestoneCount: Int,
    val budgetHashHex: String,
)
data class UpdateCampaignBudgetPayload(val campaignId: UUID, val newBudgetHashHex: String)
data class RecordDonationPayload(
    val donationId: UUID,
    val donor: String,
    val campaignId: UUID,
    val amountCents: BigInteger,
    val receiptHashHex: String,
    val txRef: String,
)
data class MilestonePayload(val campaignId: UUID, val index: Int, val proofHashHex: String)

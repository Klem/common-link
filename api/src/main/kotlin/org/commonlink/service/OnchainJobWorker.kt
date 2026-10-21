package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.config.OnchainConfig
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.onchain.OnchainCodec
import org.commonlink.onchain.OnchainRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
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
 * dispatches them to [org.commonlink.onchain.OnchainRegistry], and persists the resulting tx hash/status.
 * On failure, jobs are re-queued until the 5th attempt, after which they become `FAILED`.
 *
 * Transaction boundary: [OnchainJobClaimer.claimBatch] runs in its own committed transaction
 * (external bean call, AOP proxy applies). The RUNNING state is flushed to the DB *before*
 * [process] dispatches any on-chain transaction. A crash mid-dispatch leaves jobs in RUNNING
 * (recoverable by a future operator action) rather than silently re-PENDING, which would
 * allow two concurrent workers to double-dispatch the same job.
 *
 * Notary principle: the registry only RECORDS facts the backend already decided. The worker therefore
 * never changes a campaign, payout or donation status when a job succeeds or fails (a FAILED job leaves
 * them exactly as the backend left them). The single remaining coupling to the chain is the donation
 * receipt email: it is sent (and `DonationReceipt.emailedAt` stamped) only after RECORD_DONATION succeeds,
 * so a failed donation job means the donor gets no receipt email — see the open point in
 * `.tasks/blockchain-integration-sprint.md`.
 */
@Service
@ConditionalOnProperty(prefix = "onchain.worker", name = ["enabled"], havingValue = "true")
class OnchainJobWorker(
    private val claimer: OnchainJobClaimer,
    private val repo: org.commonlink.repository.OnchainJobRepository,
    private val donationReceiptService: DonationReceiptService,
    private val client: OnchainRegistry,
    private val objectMapper: ObjectMapper,
    private val cfg: OnchainConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${onchain.worker.fixed-delay-ms}")
    fun tick() {
        val batch = claimer.claimBatch(cfg.worker.batchSize)
        if (batch.isEmpty()) return
        batch.forEach(::process)
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
            OnchainJobAction.RECORD_DONATION -> {
                val donationId = objectMapper.readValue(job.payloadJson, RecordDonationPayload::class.java).donationId
                runCatching { donationReceiptService.sendReceiptEmailIfNeeded(donationId) }
                    .onFailure { logger.error("Failed to send receipt email for donation {}", donationId, it) }
            }
            else -> Unit
        }
    }

    /**
     * Sends the job to the registry v2 function matching its action: association and campaign registration,
     * budget update, donation, payout.
     */
    private fun dispatch(job: OnchainJob): TransactionReceipt {
        return when (job.action) {
            OnchainJobAction.REGISTER_ASSOCIATION -> {
                val p = objectMapper.readValue(job.payloadJson, RegisterAssociationPayload::class.java)
                client.registerAssociation(p.address, Numeric.hexStringToByteArray(p.sirenHashHex))
            }
            OnchainJobAction.REGISTER_CAMPAIGN -> {
                val p = objectMapper.readValue(job.payloadJson, RegisterCampaignPayload::class.java)
                client.registerCampaign(
                    OnchainCodec.uuidToBytes32(p.campaignId),
                    p.association,
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
            OnchainJobAction.RECORD_PAYOUT -> {
                val p = objectMapper.readValue(job.payloadJson, RecordPayoutPayload::class.java)
                client.recordPayout(
                    OnchainCodec.uuidToBytes32(p.payoutId),
                    OnchainCodec.uuidToBytes32(p.campaignId),
                    p.amountCents,
                )
            }
        }
    }
}

// ────── Payload DTOs ──────
/** Payload of `REGISTER_ASSOCIATION` (`registerAssociation`); `sirenHashHex` hashes the legal identifier. */
data class RegisterAssociationPayload(val address: String, val sirenHashHex: String)
/** Payload of `REGISTER_CAMPAIGN` (`registerCampaign`) with the budget hash current at enqueue time. */
data class RegisterCampaignPayload(
    val campaignId: UUID,
    val association: String,
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
data class RecordPayoutPayload(val payoutId: UUID, val campaignId: UUID, val amountCents: BigInteger)

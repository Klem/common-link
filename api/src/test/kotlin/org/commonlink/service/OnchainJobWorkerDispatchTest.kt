package org.commonlink.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.config.OnchainConfig
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.onchain.OnchainCodec
import org.commonlink.onchain.OnchainRegistry
import org.commonlink.repository.OnchainJobRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigInteger
import java.util.UUID

/**
 * Covers how [OnchainJobWorker] dispatches jobs to the registry v2 client: correct bytes32 encoding,
 * success bookkeeping, retry / terminal-failure bookkeeping.
 */
class OnchainJobWorkerDispatchTest {

    private val claimer = mockk<OnchainJobClaimer>()
    private val repo = mockk<OnchainJobRepository>(relaxed = true)
    private val client = mockk<OnchainRegistry>()
    private val objectMapper = jacksonObjectMapper()

    private val cfg = OnchainConfig(
        rpcUrl = "http://localhost:8545",
        chainId = 31337,
        registryAddress = "0x0000000000000000000000000000000000000000",
        recorderPk = "pk",
        pollingIntervalMs = 1,
        receiptTimeoutMs = 1,
        donorAddressSecret = "s",
        associationAddressSecret = "s",
        mock = false,
        worker = OnchainConfig.WorkerConfig(enabled = true, batchSize = 10, fixedDelayMs = 1),
    )

    private lateinit var worker: OnchainJobWorker

    private val payoutId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        // A relaxed mock would return a bare Object for the generic save(); echo the entity instead.
        every { repo.save(any<OnchainJob>()) } answers { firstArg() }
        worker = OnchainJobWorker(claimer, repo, mockk<DonationReceiptService>(), client, objectMapper, cfg)
    }

    private fun payoutJob(attempts: Int = 1, amountCents: BigInteger = BigInteger.valueOf(1050)) = OnchainJob(
        action = OnchainJobAction.RECORD_PAYOUT,
        payloadJson = objectMapper.writeValueAsString(RecordPayoutPayload(payoutId, campaignId, amountCents)),
        status = OnchainJobStatus.RUNNING,
        attempts = attempts,
        correlationKey = "PAYOUT:$payoutId",
    )

    private fun receipt(): TransactionReceipt = TransactionReceipt().apply {
        transactionHash = "0x" + "ab".repeat(32)
        setBlockNumber("0x10")
    }

    @Test
    fun `RECORD_PAYOUT is dispatched with bytes32-encoded ids and the exact cents`() {
        val job = payoutJob()
        every { claimer.claimBatch(10) } returns listOf(job)
        val payout = slot<ByteArray>()
        val campaign = slot<ByteArray>()
        val cents = slot<BigInteger>()
        every { client.recordPayout(capture(payout), capture(campaign), capture(cents)) } returns receipt()

        worker.tick()

        assertThat(payout.captured).isEqualTo(OnchainCodec.uuidToBytes32(payoutId))
        assertThat(campaign.captured).isEqualTo(OnchainCodec.uuidToBytes32(campaignId))
        assertThat(cents.captured).isEqualTo(BigInteger.valueOf(1050))
        assertThat(job.status).isEqualTo(OnchainJobStatus.DONE)
        assertThat(job.txHash).isEqualTo("0x" + "ab".repeat(32))
        assertThat(job.blockNumber).isEqualTo(16L)
        verify(exactly = 1) { repo.save(job) }
    }

    @Test
    fun `a client failure re-queues the job while attempts remain`() {
        val job = payoutJob(attempts = 2)
        every { claimer.claimBatch(10) } returns listOf(job)
        every { client.recordPayout(any(), any(), any()) } throws IllegalStateException("CampaignDoesNotExist")

        worker.tick()

        assertThat(job.status).isEqualTo(OnchainJobStatus.PENDING)
        assertThat(job.lastError).contains("CampaignDoesNotExist")
        assertThat(job.txHash).isNull()
    }

    @Test
    fun `a client failure on the fifth attempt marks the job FAILED`() {
        val job = payoutJob(attempts = 5)
        every { claimer.claimBatch(10) } returns listOf(job)
        every { client.recordPayout(any(), any(), any()) } throws IllegalStateException("revert")

        worker.tick()

        assertThat(job.status).isEqualTo(OnchainJobStatus.FAILED)
    }

    @Test
    fun `REGISTER_ASSOCIATION is dispatched as registerAssociation`() {
        val sirenHex = "0x" + "11".repeat(32)
        val job = OnchainJob(
            action = OnchainJobAction.REGISTER_ASSOCIATION,
            payloadJson = objectMapper.writeValueAsString(RegisterAssociationPayload("0xasso", sirenHex)),
            status = OnchainJobStatus.RUNNING,
            attempts = 1,
        )
        every { claimer.claimBatch(10) } returns listOf(job)
        val siren = slot<ByteArray>()
        every { client.registerAssociation("0xasso", capture(siren)) } returns receipt()

        worker.tick()

        assertThat(siren.captured).isEqualTo(ByteArray(32) { 0x11 })
        assertThat(job.status).isEqualTo(OnchainJobStatus.DONE)
    }

    @Test
    fun `REGISTER_CAMPAIGN is dispatched as registerCampaign with its budget hash`() {
        val budgetHex = "0x" + "22".repeat(32)
        val job = OnchainJob(
            action = OnchainJobAction.REGISTER_CAMPAIGN,
            payloadJson = objectMapper.writeValueAsString(RegisterCampaignPayload(campaignId, "0xasso", budgetHex)),
            status = OnchainJobStatus.RUNNING,
            attempts = 1,
        )
        every { claimer.claimBatch(10) } returns listOf(job)
        val id = slot<ByteArray>()
        val budget = slot<ByteArray>()
        every { client.registerCampaign(capture(id), "0xasso", capture(budget)) } returns receipt()

        worker.tick()

        assertThat(id.captured).isEqualTo(OnchainCodec.uuidToBytes32(campaignId))
        assertThat(budget.captured).isEqualTo(ByteArray(32) { 0x22 })
        assertThat(job.status).isEqualTo(OnchainJobStatus.DONE)
    }
}

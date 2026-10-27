package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.config.OnchainConfig
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.onchain.ExplorerLinkBuilder
import org.commonlink.repository.OnchainJobRepository
import org.junit.jupiter.api.Test
import java.time.Instant

/** [OnchainOutboxReportService]: counts scoped to the current chain, failed jobs with URLs masked. */
class OnchainOutboxReportServiceTest {

    private val jobs = mockk<OnchainJobRepository>()

    private fun cfg(mock: Boolean) = OnchainConfig(
        rpcUrl = "https://rpc.example.org",
        chainId = 10200,
        registryAddress = "0x5FbDB2315678afecb367f032d93F642f64180aa3",
        recorderPk = "0xabc",
        pauserPk = "0xdef",
        pollingIntervalMs = 1,
        receiptTimeoutMs = 1,
        donorAddressSecret = "s",
        associationAddressSecret = "s",
        mock = mock,
        worker = OnchainConfig.WorkerConfig(enabled = true, batchSize = 10, fixedDelayMs = 1),
        explorer = OnchainConfig.ExplorerConfig("https://gnosis-chiado.blockscout.com"),
    )

    private fun service(mock: Boolean) = cfg(mock).let { OnchainOutboxReportService(it, jobs, ExplorerLinkBuilder(it)) }

    @Test
    fun `health counts the jobs of the current chain only`() {
        val oldest = Instant.parse("2026-10-27T10:33:23Z")
        every { jobs.countByChainIdAndStatus(10200, OnchainJobStatus.PENDING) } returns 4
        every { jobs.countByChainIdAndStatus(10200, OnchainJobStatus.RUNNING) } returns 1
        every { jobs.countByChainIdAndStatus(10200, OnchainJobStatus.FAILED) } returns 2
        every { jobs.countBlockedByFailedDependency(10200) } returns 3
        every { jobs.findFirstByChainIdAndStatusOrderByCreatedAtAsc(10200, OnchainJobStatus.PENDING) } returns
            OnchainJob(action = OnchainJobAction.RECORD_DONATION, payloadJson = "{}", chainId = 10200, createdAt = oldest)

        val health = service(mock = false).health()

        assertThat(health.chainId).isEqualTo(10200)
        assertThat(health.pending).isEqualTo(4)
        assertThat(health.running).isEqualTo(1)
        assertThat(health.failed).isEqualTo(2)
        assertThat(health.blockedByFailedDependency).isEqualTo(3)
        assertThat(health.oldestPendingSince).isEqualTo(oldest)
        verify(exactly = 0) { jobs.countByChainIdAndStatus(0, any()) }
    }

    @Test
    fun `health in simulated mode reads the simulated chain, no pending job gives no date`() {
        every { jobs.countByChainIdAndStatus(0, any()) } returns 0
        every { jobs.countBlockedByFailedDependency(0) } returns 0
        every { jobs.findFirstByChainIdAndStatusOrderByCreatedAtAsc(0, OnchainJobStatus.PENDING) } returns null

        val health = service(mock = true).health()

        assertThat(health.chainId).isZero()
        assertThat(health.oldestPendingSince).isNull()
    }

    @Test
    fun `failed jobs mask every URL of the last error`() {
        val job = OnchainJob(action = OnchainJobAction.RECORD_PAYOUT, payloadJson = "{}", chainId = 10200,
            status = OnchainJobStatus.FAILED, attempts = 5,
            lastError = "Failed to connect to https://gnosis.example.org/v1/SECRET-KEY?x=1 then wss://ws.example.org/KEY2")
        every { jobs.findTop100ByChainIdAndStatusOrderByCreatedAtDesc(10200, OnchainJobStatus.FAILED) } returns listOf(job)

        val failed = service(mock = false).failedJobs().single()

        assertThat(failed.id).isEqualTo(job.id)
        assertThat(failed.action).isEqualTo("RECORD_PAYOUT")
        assertThat(failed.attempts).isEqualTo(5)
        assertThat(failed.error).isEqualTo("Failed to connect to <url> then <url>")
        assertThat(failed.error).doesNotContain("SECRET-KEY").doesNotContain("KEY2")
    }

    @Test
    fun `the live feed lists every status of the current chain with explorer links and masked errors`() {
        val done = OnchainJob(action = OnchainJobAction.RECORD_DONATION, payloadJson = "{}", chainId = 10200,
            status = OnchainJobStatus.DONE, attempts = 1, txHash = "0xabc", blockNumber = 7)
        val failed = OnchainJob(action = OnchainJobAction.RECORD_PAYOUT, payloadJson = "{}", chainId = 10200,
            status = OnchainJobStatus.FAILED, attempts = 5, lastError = "down: https://rpc.example.org/KEY")
        every { jobs.findTop50ByChainIdOrderByCreatedAtDesc(10200) } returns listOf(done, failed)

        val feed = service(mock = false).recentJobs()

        assertThat(feed.chainId).isEqualTo(10200)
        assertThat(feed.simulated).isFalse()
        assertThat(feed.items.map { it.status }).containsExactly("DONE", "FAILED")
        assertThat(feed.items[0].explorerTxUrl).isEqualTo("https://gnosis-chiado.blockscout.com/tx/0xabc")
        assertThat(feed.items[0].blockNumber).isEqualTo(7)
        assertThat(feed.items[1].explorerTxUrl).isNull()
        assertThat(feed.items[1].error).isEqualTo("down: <url>")
    }

    @Test
    fun `the live feed in simulated mode reads chain 0, is flagged simulated and links nowhere`() {
        val job = OnchainJob(action = OnchainJobAction.RECORD_DONATION, payloadJson = "{}", chainId = 0,
            status = OnchainJobStatus.DONE, txHash = "0xrandom", blockNumber = 1)
        every { jobs.findTop50ByChainIdOrderByCreatedAtDesc(0) } returns listOf(job)

        val feed = service(mock = true).recentJobs()

        assertThat(feed.chainId).isZero()
        assertThat(feed.simulated).isTrue()
        assertThat(feed.items.single().txHash).isEqualTo("0xrandom")
        assertThat(feed.items.single().explorerTxUrl).isNull()
    }

    @Test
    fun `maskUrls keeps a null error null and an error without URL untouched`() {
        assertThat(OnchainOutboxReportService.maskUrls(null)).isNull()
        assertThat(OnchainOutboxReportService.maskUrls("execution reverted: CampaignNotRegistered(0x12)"))
            .isEqualTo("execution reverted: CampaignNotRegistered(0x12)")
    }
}

package org.commonlink.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.config.OnchainConfig
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.exception.ConflictException
import org.commonlink.repository.OnchainJobRepository
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class OnchainOutboxServiceTest {

    private val repo = mockk<OnchainJobRepository>()

    private fun cfg(mock: Boolean) = OnchainConfig(
        rpcUrl = "http://localhost:8545",
        chainId = 10200,
        registryAddress = "0x0000000000000000000000000000000000000001",
        recorderPk = "pk",
        pollingIntervalMs = 1,
        receiptTimeoutMs = 1,
        donorAddressSecret = "s",
        associationAddressSecret = "s",
        mock = mock,
        worker = OnchainConfig.WorkerConfig(enabled = false, batchSize = 10, fixedDelayMs = 1),
    )

    private fun service(mock: Boolean) = OnchainOutboxService(repo, jacksonObjectMapper(), cfg(mock))

    init {
        every { repo.save(any<OnchainJob>()) } answers { firstArg() }
        every { repo.saveAndFlush(any<OnchainJob>()) } answers { firstArg() }
    }

    @Test
    fun `a key inserted concurrently since the lookup is refused with a 409`() {
        every { repo.findByChainIdAndCorrelationKey(10200, "K") } returns null
        every { repo.saveAndFlush(any<OnchainJob>()) } throws DataIntegrityViolationException("uq_onchain_jobs_chain_correlation")

        assertThatThrownBy {
            service(mock = false).enqueue(OnchainJobAction.UPDATE_CAMPAIGN_BUDGET, mapOf("a" to 1), "K")
        }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `a job without correlation key is saved without flush`() {
        service(mock = false).enqueue(OnchainJobAction.RECORD_DONATION, mapOf("a" to 1))

        verify { repo.save(any<OnchainJob>()) }
        verify(exactly = 0) { repo.saveAndFlush(any<OnchainJob>()) }
    }

    @Test
    fun `mock mode writes jobs on chain 0`() {
        every { repo.findByChainIdAndCorrelationKey(0, "K") } returns null

        val job = service(mock = true).enqueue(OnchainJobAction.RECORD_DONATION, mapOf("a" to 1), "K")

        assertThat(job.chainId).isEqualTo(0)
    }

    @Test
    fun `real mode writes jobs on the configured chain and records the dependency`() {
        val prerequisite = OnchainJob(action = OnchainJobAction.REGISTER_CAMPAIGN, payloadJson = "{}", chainId = 10200)
        every { repo.findByChainIdAndCorrelationKey(10200, "K") } returns null

        val job = service(mock = false).enqueue(OnchainJobAction.RECORD_DONATION, mapOf("a" to 1), "K", prerequisite)

        assertThat(job.chainId).isEqualTo(10200)
        assertThat(job.dependsOnJobId).isEqualTo(prerequisite.id)
    }

    @Test
    fun `idempotency is scoped to the current chain`() {
        val existing = OnchainJob(action = OnchainJobAction.RECORD_DONATION, payloadJson = "{}", chainId = 10200, correlationKey = "K")
        every { repo.findByChainIdAndCorrelationKey(10200, "K") } returns existing

        val job = service(mock = false).enqueue(OnchainJobAction.RECORD_DONATION, mapOf("a" to 1), "K")

        assertThat(job).isSameAs(existing)
        verify(exactly = 0) { repo.save(any<OnchainJob>()) }
    }

    @Test
    fun `a dependency on another chain is refused`() {
        val mockJob = OnchainJob(action = OnchainJobAction.REGISTER_CAMPAIGN, payloadJson = "{}", chainId = 0)

        assertThatThrownBy {
            service(mock = false).enqueue(OnchainJobAction.RECORD_DONATION, mapOf("a" to 1), "K", mockJob)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `findCurrent reads the current chain only`() {
        every { repo.findByChainIdAndCorrelationKey(10200, "DONATION:x") } returns null

        assertThat(service(mock = false).findCurrent("DONATION:x")).isNull()
        verify { repo.findByChainIdAndCorrelationKey(10200, "DONATION:x") }
    }
}

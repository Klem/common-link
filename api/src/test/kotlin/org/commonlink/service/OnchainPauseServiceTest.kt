package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.config.OnchainConfig
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.ConflictException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.onchain.ExplorerLinkBuilder
import org.commonlink.onchain.OnchainPauser
import org.commonlink.onchain.OnchainRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.io.IOException
import java.util.UUID

/** [OnchainPauseService]: direct pause, idempotence, journal of every outcome, simulated mode refused. */
class OnchainPauseServiceTest {

    private val registry = mockk<OnchainRegistry>()
    private val pauser = mockk<OnchainPauser>()
    private val auditLog = mockk<ComplianceAuditLogService>()
    private val officer = UUID.randomUUID()
    private val payload = slot<Any>()

    private fun cfg(mock: Boolean = false) = OnchainConfig(
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

    private fun service(mock: Boolean = false, pauserBean: OnchainPauser? = pauser): OnchainPauseService {
        val provider = mockk<ObjectProvider<OnchainPauser>> { every { getIfAvailable() } returns pauserBean }
        val c = cfg(mock)
        every { auditLog.append(any(), any(), capture(payload), any(), any()) } returns mockk<ComplianceAuditLog>()
        return OnchainPauseService(c, registry, provider, auditLog, ExplorerLinkBuilder(c))
    }

    @Suppress("UNCHECKED_CAST")
    private fun captured(): Map<String, Any?> = payload.captured as Map<String, Any?>

    private fun receipt(status: String = "0x1") = TransactionReceipt().apply {
        transactionHash = "0x" + "ab".repeat(32)
        setBlockNumber("0x2a")
        this.status = status
    }

    @Test
    fun `pauses the registry with the pauser key and journals the officer, rationale and transaction`() {
        every { registry.isPaused() } returns false
        every { pauser.pause() } returns receipt()

        val result = service().pause(officer, "Clé recorder compromise")

        assertThat(result.paused).isTrue()
        assertThat(result.alreadyPaused).isFalse()
        assertThat(result.txHash).isEqualTo("0x" + "ab".repeat(32))
        assertThat(result.blockNumber).isEqualTo(42)
        assertThat(result.explorerTxUrl).isEqualTo("https://gnosis-chiado.blockscout.com/tx/0x" + "ab".repeat(32))
        verify(exactly = 1) { pauser.pause() }
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSED, ComplianceAuditSubjectType.SYSTEM, any(), null, officer)
        }
        assertThat(captured()).containsEntry("rationale", "Clé recorder compromise")
            .containsEntry("txHash", "0x" + "ab".repeat(32)).containsEntry("alreadyPaused", false).containsEntry("chainId", 10200L)
    }

    @Test
    fun `an already paused registry gets no transaction but the request is journaled`() {
        every { registry.isPaused() } returns true

        val result = service().pause(officer, "Double vérification")

        assertThat(result.alreadyPaused).isTrue()
        assertThat(result.txHash).isNull()
        verify(exactly = 0) { pauser.pause() }
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSED, ComplianceAuditSubjectType.SYSTEM, any(), null, officer)
        }
        assertThat(captured()).containsEntry("alreadyPaused", true)
    }

    @Test
    fun `a failed pause is journaled with the error type only, then reported as 502`() {
        every { registry.isPaused() } returns false
        every { pauser.pause() } throws IOException("Failed to connect to https://rpc.example.org/secret-token")

        assertThatThrownBy { service().pause(officer, "Incident") }
            .isInstanceOf(BadGatewayException::class.java)
            .hasMessageNotContaining("secret-token")
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSE_FAILED, ComplianceAuditSubjectType.SYSTEM, any(), null, officer)
        }
        assertThat(captured()).containsEntry("errorType", "IOException")
            .containsEntry("rationale", "Incident")
        assertThat(payload.captured.toString()).doesNotContain("secret-token")
    }

    @Test
    fun `a reverted pause transaction is a failure`() {
        every { registry.isPaused() } returns false
        every { pauser.pause() } returns receipt(status = "0x0")

        assertThatThrownBy { service().pause(officer, "Incident") }.isInstanceOf(BadGatewayException::class.java)
        verify(exactly = 1) { auditLog.append(ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSE_FAILED, any(), any(), any(), any()) }
    }

    @Test
    fun `an unreadable pause state is a failure, nothing is sent`() {
        every { registry.isPaused() } throws IOException("down")

        assertThatThrownBy { service().pause(officer, "Incident") }.isInstanceOf(BadGatewayException::class.java)
        verify(exactly = 0) { pauser.pause() }
    }

    @Test
    fun `a blank rationale is refused before anything happens`() {
        assertThatThrownBy { service().pause(officer, "   ") }.isInstanceOf(UnprocessableEntityException::class.java)
        verify(exactly = 0) { registry.isPaused() }
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `simulated mode has no registry to freeze`() {
        assertThatThrownBy { service(mock = true, pauserBean = null).pause(officer, "Incident") }
            .isInstanceOf(ConflictException::class.java)
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `status reads the pause state, null when the chain cannot be read`() {
        every { registry.isPaused() } returns true
        assertThat(service().status().paused).isTrue()

        every { registry.isPaused() } throws IOException("down")
        val status = service().status()
        assertThat(status.paused).isNull()
        assertThat(status.mock).isFalse()
        assertThat(status.explorerAddressUrl)
            .isEqualTo("https://gnosis-chiado.blockscout.com/address/0x5FbDB2315678afecb367f032d93F642f64180aa3")
    }

    @Test
    fun `status in simulated mode never touches a chain`() {
        val status = service(mock = true, pauserBean = null).status()
        assertThat(status.mock).isTrue()
        assertThat(status.paused).isFalse()
        assertThat(status.chainId).isZero()
        assertThat(status.registryAddress).isNull()
        verify(exactly = 0) { registry.isPaused() }
    }
}

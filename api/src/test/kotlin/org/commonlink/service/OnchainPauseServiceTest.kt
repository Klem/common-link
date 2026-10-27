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
import org.commonlink.entity.User
import org.commonlink.repository.UserRepository
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.io.IOException
import java.util.UUID

/** [OnchainPauseService]: direct pause and resume, idempotence, journal of every outcome, simulated mode refused. */
class OnchainPauseServiceTest {

    private val registry = mockk<OnchainRegistry>()
    private val pauser = mockk<OnchainPauser>()
    private val auditLog = mockk<ComplianceAuditLogService>()
    private val users = mockk<UserRepository>()
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
        return OnchainPauseService(c, registry, provider, auditLog, ExplorerLinkBuilder(c), users)
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
        every { pauser.canUnpause() } returns true
        assertThat(service().status().paused).isTrue()
        assertThat(service().status().canUnpause).isTrue()

        every { registry.isPaused() } throws IOException("down")
        every { pauser.canUnpause() } throws IOException("down")
        val status = service().status()
        assertThat(status.paused).isNull()
        assertThat(status.canUnpause).isFalse()
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
        assertThat(status.canUnpause).isFalse()
        verify(exactly = 0) { registry.isPaused() }
    }

    @Test
    fun `status reports a revoked resume right`() {
        every { registry.isPaused() } returns true
        every { pauser.canUnpause() } returns false
        assertThat(service().status().canUnpause).isFalse()
    }

    @Test
    fun `history resolves the officers' emails in one lookup, unknown accounts give null`() {
        val gone = UUID.randomUUID()
        fun entry(seq: Long, actor: UUID?) = ComplianceAuditLog(sequenceNo = seq,
            eventType = ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSED, subjectType = ComplianceAuditSubjectType.SYSTEM,
            payload = "{}", actorUserId = actor, occurredAt = Instant.EPOCH, rowHash = "h")
        every { auditLog.findOnchainRegistryControlHistory() } returns listOf(entry(3, officer), entry(2, gone), entry(1, officer))
        val officerUser = mockk<User> { every { id } returns officer; every { email } returns "compliance@example.org" }
        every { users.findAllById(setOf(officer, gone)) } returns listOf(officerUser)

        val history = service().history()

        assertThat(history.map { it.actorEmail }).containsExactly("compliance@example.org", null, "compliance@example.org")
        assertThat(history.map { it.sequenceNo }).containsExactly(3L, 2L, 1L)
        verify(exactly = 1) { users.findAllById(any()) }
    }

    // ───── Resume ─────

    @Test
    fun `resumes the registry with the pauser key and journals the officer, rationale and transaction`() {
        every { registry.isPaused() } returns true
        every { pauser.canUnpause() } returns true
        every { pauser.unpause() } returns receipt()

        val result = service().unpause(officer, "Incident clos")

        assertThat(result.paused).isFalse()
        assertThat(result.alreadyActive).isFalse()
        assertThat(result.txHash).isEqualTo("0x" + "ab".repeat(32))
        assertThat(result.blockNumber).isEqualTo(42)
        verify(exactly = 1) { pauser.unpause() }
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSED, ComplianceAuditSubjectType.SYSTEM, any(), null, officer)
        }
        assertThat(captured()).containsEntry("rationale", "Incident clos")
            .containsEntry("txHash", "0x" + "ab".repeat(32)).containsEntry("alreadyActive", false)
    }

    @Test
    fun `a registry that is not paused gets no transaction but the request is journaled`() {
        every { registry.isPaused() } returns false

        val result = service().unpause(officer, "Vérification")

        assertThat(result.alreadyActive).isTrue()
        assertThat(result.txHash).isNull()
        verify(exactly = 0) { pauser.unpause() }
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSED, ComplianceAuditSubjectType.SYSTEM, any(), null, officer)
        }
        assertThat(captured()).containsEntry("alreadyActive", true)
    }

    @Test
    fun `a revoked resume right is refused with 409, journaled, nothing sent`() {
        every { registry.isPaused() } returns true
        every { pauser.canUnpause() } returns false

        assertThatThrownBy { service().unpause(officer, "Incident clos") }.isInstanceOf(ConflictException::class.java)
        verify(exactly = 0) { pauser.unpause() }
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSE_FAILED, ComplianceAuditSubjectType.SYSTEM, any(), null, officer)
        }
        assertThat(captured()).containsEntry("errorType", "UNPAUSER_ROLE_MISSING")
    }

    @Test
    fun `a failed resume is journaled with the error type only, then reported as 502`() {
        every { registry.isPaused() } returns true
        every { pauser.canUnpause() } returns true
        every { pauser.unpause() } throws IOException("Failed to connect to https://rpc.example.org/secret-token")

        assertThatThrownBy { service().unpause(officer, "Incident clos") }
            .isInstanceOf(BadGatewayException::class.java)
            .hasMessageNotContaining("secret-token")
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSE_FAILED, ComplianceAuditSubjectType.SYSTEM, any(), null, officer)
        }
        assertThat(captured()).containsEntry("errorType", "IOException")
        assertThat(payload.captured.toString()).doesNotContain("secret-token")
    }

    @Test
    fun `a reverted resume transaction is a failure`() {
        every { registry.isPaused() } returns true
        every { pauser.canUnpause() } returns true
        every { pauser.unpause() } returns receipt(status = "0x0")

        assertThatThrownBy { service().unpause(officer, "Incident clos") }.isInstanceOf(BadGatewayException::class.java)
    }

    @Test
    fun `resume - blank rationale refused before anything happens, simulated mode refused without journal`() {
        assertThatThrownBy { service().unpause(officer, " ") }.isInstanceOf(UnprocessableEntityException::class.java)
        assertThatThrownBy { service(mock = true, pauserBean = null).unpause(officer, "Incident clos") }
            .isInstanceOf(ConflictException::class.java)
        verify(exactly = 0) { registry.isPaused() }
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `two requests never send concurrently on the shared key`() {
        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        val overlap = java.util.concurrent.atomic.AtomicBoolean(false)
        fun slowReceipt(): org.web3j.protocol.core.methods.response.TransactionReceipt {
            if (inFlight.incrementAndGet() > 1) overlap.set(true)
            Thread.sleep(50)
            inFlight.decrementAndGet()
            return receipt()
        }
        // Both officers see an active registry: without the lock both pause() calls would be in flight at once.
        every { registry.isPaused() } returns false
        every { pauser.pause() } answers { slowReceipt() }
        val svc = service()

        val t1 = Thread { svc.pause(officer, "Gel 1") }
        val t2 = Thread { svc.pause(officer, "Gel 2") }
        t1.start(); t2.start(); t1.join(); t2.join()

        assertThat(overlap.get()).isFalse()
    }
}

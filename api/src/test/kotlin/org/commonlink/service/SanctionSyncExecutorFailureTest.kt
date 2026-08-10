package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.repository.ComplianceAuditLogRepository
import org.commonlink.repository.SanctionSyncStateRepository
import org.commonlink.service.ComplianceAuditLogService.Companion.SANCTION_SYNC_FAILURE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.junit.jupiter.api.Assertions.assertDoesNotThrow

/**
 * Integration tests for [SanctionSyncExecutor] — failure path.
 *
 * [SanctionIngestionRunner] is mocked to throw a [RuntimeException], simulating a registry
 * download failure. The test verifies that:
 *  - `last_success_at` is NOT updated (screening continues on the last known register).
 *  - `last_attempt_at` IS updated (the attempt is on record).
 *  - A [ComplianceAuditLogService.SANCTION_SYNC_FAILURE] audit entry is written.
 *
 * A separate Spring context is loaded because [MockkBean] changes the bean composition.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
    "commonlink.sanctions.screening.use-test-data=true",
    "commonlink.sanctions.sync.enabled=false",
])
@Sql(
    scripts = [
        "/sql/compliance_audit_log_test_schema.sql",
        "/sql/sanctions_sync_state_test_schema.sql",
    ],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
)
class SanctionSyncExecutorFailureTest {

    @MockkBean
    private lateinit var runner: SanctionIngestionRunner

    @Autowired private lateinit var executor: SanctionSyncExecutor
    @Autowired private lateinit var stateRepo: SanctionSyncStateRepository
    @Autowired private lateinit var auditRepo: ComplianceAuditLogRepository
    @Autowired private lateinit var screeningService: SanctionScreeningService
    @Autowired private lateinit var txManager: PlatformTransactionManager

    private val requiresNewTx get() = TransactionTemplate(
        txManager,
        DefaultTransactionDefinition().apply {
            propagationBehavior = DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW
        },
    )

    @Test
    fun `failed sync leaves lastSuccessAt unchanged and writes a SANCTION_SYNC_FAILURE audit entry`() {
        every { runner.run() } throws RuntimeException("simulated DG Trésor connection failure")

        // Reset state to a clean baseline
        requiresNewTx.execute {
            val state = stateRepo.findById(1.toShort()).orElseThrow()
            state.lastAttemptAt = null
            state.lastSuccessAt = null
            state.lastPublicationDate = null
            stateRepo.save(state)
        }

        val auditCountBefore = auditRepo.count()

        executor.execute()

        val state = stateRepo.findById(1.toShort()).orElseThrow()
        assertNotNull(state.lastAttemptAt, "lastAttemptAt must be set even after a failure")
        assertNull(state.lastSuccessAt, "lastSuccessAt must NOT be updated after a failure")
        assertNull(state.lastPublicationDate, "lastPublicationDate must NOT be updated after a failure")

        val newEntries = auditRepo.findAllByOrderBySequenceNoAsc()
            .drop(auditCountBefore.toInt())
        assertEquals(1, newEntries.size, "exactly one audit entry must be written for the failure")
        assertEquals(SANCTION_SYNC_FAILURE, newEntries.single().eventType)

        verify(exactly = 1) { runner.run() }

        // Cleanup
        requiresNewTx.execute { auditRepo.deleteAllById(newEntries.map { it.id }) }
    }

    @Test
    fun `screening service remains operational after a failed sync`() {
        every { runner.run() } throws RuntimeException("network unreachable")

        requiresNewTx.execute {
            val state = stateRepo.findById(1.toShort()).orElseThrow()
            state.lastAttemptAt = null
            state.lastSuccessAt = null
            stateRepo.save(state)
        }

        val auditCountBefore = auditRepo.count()

        // The failed sync must not throw to the caller
        executor.execute()

        // Screening must still return a result (not throw) — proves the register is still queryable
        val result = assertDoesNotThrow { screeningService.screen("Jean Dupont") }
        assertNotNull(result, "screen() must return a non-null list even when the last sync failed")

        // Cleanup audit entry written by the failure
        val newEntries = auditRepo.findAllByOrderBySequenceNoAsc().drop(auditCountBefore.toInt())
        requiresNewTx.execute { auditRepo.deleteAllById(newEntries.map { it.id }) }
    }
}

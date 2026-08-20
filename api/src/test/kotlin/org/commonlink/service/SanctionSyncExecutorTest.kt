package org.commonlink.service

import org.commonlink.repository.SanctionSyncStateRepository
import org.junit.jupiter.api.AfterEach
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

/**
 * Integration tests for [SanctionSyncExecutor] — success path.
 *
 * Uses the H2 test profile (Flyway off, Hibernate create-drop) and the bundled test fixture
 * (`use-test-data=true`) so no live DG Trésor network call is made.
 *
 * Scheduler is disabled (`commonlink.sanctions.sync.enabled=false`) so the background job
 * does not fire during the test run; [SanctionSyncExecutor] is called directly.
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
class SanctionSyncExecutorTest {

    @Autowired private lateinit var executor: SanctionSyncExecutor
    @Autowired private lateinit var stateRepo: SanctionSyncStateRepository
    @Autowired private lateinit var txManager: PlatformTransactionManager

    @AfterEach
    fun resetState() {
        // Reset state after each test so tests are independent
        val requiresNew = org.springframework.transaction.support.TransactionTemplate(
            txManager,
            DefaultTransactionDefinition().apply {
                propagationBehavior = DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW
            },
        )
        requiresNew.execute {
            val state = stateRepo.findById(1.toShort()).orElseThrow()
            state.lastAttemptAt = null
            state.lastSuccessAt = null
            state.lastPublicationDate = null
            stateRepo.save(state)
        }
    }

    @Test
    fun `execute updates lastSuccessAt and lastPublicationDate on successful ingestion`() {
        executor.execute()

        val state = stateRepo.findById(1.toShort()).orElseThrow()
        assertNotNull(state.lastAttemptAt, "lastAttemptAt must be set after a sync attempt")
        assertNotNull(state.lastSuccessAt, "lastSuccessAt must be set after a successful sync")
        assertNotNull(state.lastPublicationDate, "lastPublicationDate must reflect the ingested register")
    }

    @Test
    fun `execute sets lastAttemptAt even when called with already-synced state`() {
        // First call — establishes a baseline
        executor.execute()
        val firstState = stateRepo.findById(1.toShort()).orElseThrow()
        val firstSuccessAt = firstState.lastSuccessAt

        // Second call — re-sync should update lastAttemptAt and keep lastSuccessAt ≥ previous
        executor.execute()
        val secondState = stateRepo.findById(1.toShort()).orElseThrow()

        assertNotNull(secondState.lastAttemptAt)
        assertNotNull(secondState.lastSuccessAt)
        assert(secondState.lastSuccessAt!! >= firstSuccessAt!!) {
            "lastSuccessAt must not regress after a second successful sync"
        }
    }
}

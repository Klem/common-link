package org.commonlink.service

import org.commonlink.dto.FreezeScreenStatus
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.repository.ComplianceAuditLogRepository
import org.junit.jupiter.api.AfterEach
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
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import jakarta.persistence.EntityManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Covers everything about [ComplianceAuditLogService] that doesn't depend on Postgres-only DDL
 * (the V51 migration's trigger and `REVOKE`) — hash chaining, reproducibility, tamper detection
 * via hash mismatch, and write serialization. Runs on the suite's default `create-drop`/H2
 * schema and needs no Testcontainers/Docker at all: [org.commonlink.repository.ComplianceAuditLogRepository.acquireWriteLock]
 * is a plain `SELECT ... FOR UPDATE` on a lock row, not `pg_advisory_xact_lock` — deliberately
 * chosen so this logic is testable without a real Postgres, and it's just as valid in production.
 *
 * Deliberately NOT covered here: the trigger/`REVOKE` actually rejecting `UPDATE`/`DELETE` at the
 * database level — H2 has no PL/pgSQL and this test connection is effectively a superuser, so
 * neither guard exists on this schema (which is also why this class, unlike production, CAN
 * clean up after itself with a plain `DELETE`). That DB-level guard proof is
 * [ComplianceAuditLogServiceTest], which requires real Postgres and stays `@Disabled` here.
 *
 * `create-drop` builds its schema purely from `@Entity` mappings, so V51's hand-written
 * `compliance_audit_log_seq` sequence and `compliance_audit_log_lock` table — neither backed by
 * an entity, by design — don't exist here (same limitation [MandateServiceTest] documents for
 * `fiscal_mandate_ref_seq`). `compliance_audit_log_test_schema.sql` provisions both: plain,
 * portable ANSI SQL (unlike the trigger/`REVOKE`), so this is a faithful stand-in, not a parallel
 * implementation. Run at `BEFORE_TEST_CLASS` specifically — not per-method, and not inline in a
 * `@BeforeEach` — because that phase commits in its own transaction, independent of the
 * per-method [Transactional] rollback wrapper. A `@BeforeEach` would leave the sequence/table
 * uncommitted and invisible to the concurrency test's worker threads, which use their own
 * separate connections.
 *
 * ### An H2-only quirk this class works around
 *
 * H2's `Instant` round-trip through a plain (non-timezone-aware) `TIMESTAMP` column is not
 * reliable in this environment — a fresh read can come back shifted by the JVM's local UTC
 * offset (confirmed empirically; neither `@JdbcTypeCode(TIMESTAMP_UTC)` nor
 * `hibernate.jdbc.time_zone=UTC` fixed it, since the generated `create-drop` column is
 * timezone-naive either way). Real Postgres has no such issue — `TIMESTAMPTZ` (V51) is
 * timezone-transparent by design — so this is purely a limitation of testing against H2, not a
 * production defect.
 *
 * Two consequences:
 * - The tamper test explicitly [EntityManager.refresh]es only the tampered row before verifying
 *   (a genuine fresh read for that one row is fine — it's supposed to look broken either way);
 *   the untampered row stays cached and unaffected.
 * - The concurrency test's 20 rows are written via separate connections/threads and really
 *   commit (they bypass this class's [Transactional] rollback). Left behind, a *later* test's
 *   [ComplianceAuditLogService.verifyChain] would do a genuine fresh read of them and misreport
 *   the quirk as tampering — not because anything is actually wrong with them, but because
 *   nothing here before this fix ever re-read them fresh either. [cleanUpConcurrentRows] deletes
 *   them immediately after, via their own committing transaction (same pattern as
 *   [OnchainJobClaimerConcurrentTest]'s cleanup) — safe only because this H2 schema has no
 *   append-only guard; the real migration would reject this exact `DELETE`.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@Sql(scripts = ["/sql/compliance_audit_log_test_schema.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Transactional
class ComplianceAuditLogServiceLogicTest {

    @Autowired private lateinit var service: ComplianceAuditLogService
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var repo: ComplianceAuditLogRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val concurrentRowIds = mutableListOf<UUID>()

    /**
     * This class carries a class-level [Transactional], so a plain [TransactionTemplate] with
     * default propagation would just join (and later get rolled back with) that ambient
     * transaction — a no-op cleanup. `REQUIRES_NEW` forces a genuinely separate, independently
     * committing transaction, mirroring [OnchainJobClaimerConcurrentTest]'s cleanup (which has no
     * such ambient transaction to conflict with in the first place).
     */
    @AfterEach
    fun cleanUpConcurrentRows() {
        if (concurrentRowIds.isEmpty()) return
        val requiresNew = TransactionTemplate(transactionManager, DefaultTransactionDefinition().apply {
            propagationBehavior = DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW
        })
        requiresNew.execute { repo.deleteAllById(concurrentRowIds) }
        concurrentRowIds.clear()
    }

    private fun append(eventType: String = "TEST_EVENT") = service.append(
        eventType = eventType,
        subjectType = ComplianceAuditSubjectType.ASSOCIATION,
        payload = mapOf("note" to "test-$eventType"),
        subjectId = UUID.randomUUID(),
    )

    @Test
    fun `three successive writes produce a coherent, reproducibly verifiable chain`() {
        val first = append("EVENT_1")
        val second = append("EVENT_2")
        val third = append("EVENT_3")

        assertEquals(second.prevHash, first.rowHash)
        assertEquals(third.prevHash, second.rowHash)

        // Reproducibility: recomputing from the same rows twice must agree both times.
        assertNull(service.verifyChain())
        assertNull(service.verifyChain())
    }

    @Test
    fun `tampering with a payload is caught by verifyChain via hash mismatch`() {
        val target = append("WILL_BE_TAMPERED")
        append("AFTER_TAMPERED_ROW")

        entityManager.createNativeQuery(
            "UPDATE compliance_audit_log SET payload = :payload WHERE id = :id",
        ).setParameter("payload", """{"note":"forged"}""").setParameter("id", target.id).executeUpdate()

        // The native UPDATE bypassed the ORM, so Hibernate's session still holds the stale,
        // pre-tamper `target` — without this, verifyChain() would recompute from that stale
        // object and (wrongly) report the chain as intact. Refresh only `target`, not the whole
        // session: see the class KDoc on the H2 occurred_at round-trip quirk.
        entityManager.refresh(target)

        assertEquals(target.sequenceNo, service.verifyChain())
    }

    @Test
    fun `20 concurrent writes produce 20 distinct, gapless, chained sequence numbers`() {
        val executor = Executors.newFixedThreadPool(20)
        val ready = CountDownLatch(20)

        val futures = (1..20).map {
            executor.submit<ComplianceAuditLog> {
                ready.countDown()
                ready.await()
                service.append(
                    eventType = "CONCURRENT_EVENT",
                    subjectType = ComplianceAuditSubjectType.DONATION,
                    payload = mapOf("worker" to it),
                )
            }
        }
        val rows = futures.map { it.get() }
        executor.shutdown()
        concurrentRowIds += rows.map { it.id }

        val sequenceNos = rows.map { it.sequenceNo }.sorted()
        assertEquals(20, sequenceNos.toSet().size, "sequence_no must be unique across concurrent writers")
        for (i in 1 until sequenceNos.size) {
            assertEquals(sequenceNos[i - 1] + 1, sequenceNos[i], "no gap allowed between concurrent writes")
        }

        val bySeq = rows.associateBy { it.sequenceNo }
        for (row in rows) {
            if (row.sequenceNo == sequenceNos.first()) continue
            val predecessor = bySeq[row.sequenceNo - 1] ?: continue
            assertEquals(predecessor.rowHash, row.prevHash)
        }
        assertEquals(20, rows.map { it.rowHash }.toSet().size, "each concurrent write must produce a distinct row_hash")
    }

    // ── findLastOnboardingFreezeScreenStatus ──────────────────────────────────────────────────

    private fun appendFreeze(
        eventType: String,
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID,
    ) = service.append(
        eventType = eventType,
        subjectType = subjectType,
        payload = mapOf("test" to true),
        subjectId = subjectId,
    )

    @Test
    fun `findLastOnboardingFreezeScreenStatus - no events returns NOT_PERFORMED`() {
        val result = service.findLastOnboardingFreezeScreenStatus(UUID.randomUUID(), emptyList())
        assertEquals(FreezeScreenStatus.NOT_PERFORMED, result.status)
        assertNull(result.checkedAt)
    }

    @Test
    fun `findLastOnboardingFreezeScreenStatus - single CLEAR run returns PASSED`() {
        val assocId = UUID.randomUUID()
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_CLEAR, ComplianceAuditSubjectType.ASSOCIATION, assocId)
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_CLEAR, ComplianceAuditSubjectType.DECLARANT, assocId)

        val result = service.findLastOnboardingFreezeScreenStatus(assocId, emptyList())
        assertEquals(FreezeScreenStatus.PASSED, result.status)
        assertNotNull(result.checkedAt)
    }

    @Test
    fun `findLastOnboardingFreezeScreenStatus - run1 HIT followed by run2 CLEAR returns PASSED`() {
        val assocId = UUID.randomUUID()
        // Run 1 (earlier): association hit
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_HIT, ComplianceAuditSubjectType.ASSOCIATION, assocId)
        // Run 2 (most recent): full clear — sequenceNo of this ASSOCIATION event becomes runStartSeq
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_CLEAR, ComplianceAuditSubjectType.ASSOCIATION, assocId)
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_CLEAR, ComplianceAuditSubjectType.DECLARANT, assocId)

        val result = service.findLastOnboardingFreezeScreenStatus(assocId, emptyList())
        assertEquals(FreezeScreenStatus.PASSED, result.status)
    }

    @Test
    fun `findLastOnboardingFreezeScreenStatus - BO HIT with assoc and declarant clear returns HIT`() {
        val assocId = UUID.randomUUID()
        val boId = UUID.randomUUID()
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_CLEAR, ComplianceAuditSubjectType.ASSOCIATION, assocId)
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_CLEAR, ComplianceAuditSubjectType.DECLARANT, assocId)
        // BO events use subject_id = bo.id, not associationId
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_HIT, ComplianceAuditSubjectType.BENEFICIAL_OWNER, boId)

        val result = service.findLastOnboardingFreezeScreenStatus(assocId, listOf(boId))
        assertEquals(FreezeScreenStatus.HIT, result.status)
    }

    @Test
    fun `findLastOnboardingFreezeScreenStatus - UNAVAILABLE event returns UNAVAILABLE`() {
        val assocId = UUID.randomUUID()
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_CLEAR, ComplianceAuditSubjectType.ASSOCIATION, assocId)
        appendFreeze(ComplianceAuditLogService.FREEZE_SCREENING_UNAVAILABLE, ComplianceAuditSubjectType.ASSOCIATION, assocId)

        val result = service.findLastOnboardingFreezeScreenStatus(assocId, emptyList())
        assertEquals(FreezeScreenStatus.UNAVAILABLE, result.status)
    }
}

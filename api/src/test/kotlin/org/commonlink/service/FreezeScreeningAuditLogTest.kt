package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.repository.ComplianceAuditLogRepository
import org.commonlink.service.ComplianceAuditLogService.Companion.FREEZE_SCREENING_CLEAR
import org.commonlink.service.ComplianceAuditLogService.Companion.FREEZE_SCREENING_HIT
import org.commonlink.service.ComplianceAuditLogService.Companion.FREEZE_SCREENING_UNAVAILABLE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.time.LocalDate
import java.util.UUID

/**
 * Verifies the freeze-screening audit helpers on [ComplianceAuditLogService]:
 * - Three event types are produced with the correct structure.
 * - No subject identity appears in any payload (whitelist assertion on exact key set).
 * - Per-subject history is correctly filtered by [ComplianceAuditLogService.findFreezeScreeningHistory].
 * - Successive freeze-screening events are linked by the hash chain.
 * - CLEAR followed by HIT in the same logical flow does not deadlock (uniform REQUIRES_NEW).
 *
 * Test setup mirrors [ComplianceAuditLogServiceLogicTest]: same `@Sql` bootstrapping the
 * sequence and lock table, same `@ActiveProfiles`, same `@Transactional` rollback per test.
 *
 * ### REQUIRES_NEW cleanup
 *
 * All three typed helpers use [org.springframework.transaction.annotation.Propagation.REQUIRES_NEW]
 * and commit independently of the test's ambient transaction. Every row produced by a helper is
 * tracked in [requiresNewRowIds] and deleted in [cleanUpRequiresNewRows] — safe here because H2
 * has no append-only trigger.
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
class FreezeScreeningAuditLogTest {

    @Autowired private lateinit var service: ComplianceAuditLogService
    @Autowired private lateinit var repo: ComplianceAuditLogRepository
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val requiresNewRowIds = mutableListOf<UUID>()
    private val registryDate = LocalDate.of(2026, 8, 10)

    @AfterEach
    fun cleanUpRequiresNewRows() {
        if (requiresNewRowIds.isEmpty()) return
        val requiresNew = TransactionTemplate(transactionManager, DefaultTransactionDefinition().apply {
            propagationBehavior = DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW
        })
        requiresNew.execute { repo.deleteAllById(requiresNewRowIds) }
        requiresNewRowIds.clear()
    }

    // ---- CLEAR -------------------------------------------------------------------------------

    @Test
    fun `CLEAR event has correct type and payload contains only whitelisted keys`() {
        val event = service.appendFreezeScreeningClear(
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = UUID.randomUUID(),
            registryPublicationDate = registryDate,
            scoreThreshold = 0.85,
        )
        requiresNewRowIds += event.id

        assertEquals(FREEZE_SCREENING_CLEAR, event.eventType)

        val payload = objectMapper.readValue(event.payload, Map::class.java)
        assertEquals(
            setOf("registryPublicationDate", "scoreThreshold", "matchCount", "nature"),
            payload.keys,
            "CLEAR payload must contain exactly these keys — no subject identity in clear",
        )
        assertEquals(0, payload["matchCount"])
        assertEquals(registryDate.toString(), payload["registryPublicationDate"])
    }

    // ---- HIT ---------------------------------------------------------------------------------

    @Test
    fun `HIT event has correct type and payload contains only whitelisted keys`() {
        val event = service.appendFreezeScreeningHit(
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = UUID.randomUUID(),
            registryPublicationDate = registryDate,
            scoreThreshold = 0.85,
            matchCount = 2,
            topScore = 0.93,
        )
        requiresNewRowIds += event.id

        assertEquals(FREEZE_SCREENING_HIT, event.eventType)

        val payload = objectMapper.readValue(event.payload, Map::class.java)
        assertEquals(
            setOf("registryPublicationDate", "scoreThreshold", "matchCount", "topScore", "nature"),
            payload.keys,
            "HIT payload must contain exactly these keys — no subject identity in clear",
        )
        assertEquals(2, payload["matchCount"])
        assertEquals(0.93, (payload["topScore"] as Number).toDouble(), 1e-9)
    }

    // ---- UNAVAILABLE -------------------------------------------------------------------------

    @Test
    fun `UNAVAILABLE event has correct type and reason in payload`() {
        val event = service.appendFreezeScreeningUnavailable(
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = UUID.randomUUID(),
            reason = "registry download failed: HTTP 503",
        )
        requiresNewRowIds += event.id

        assertEquals(FREEZE_SCREENING_UNAVAILABLE, event.eventType)

        val payload = objectMapper.readValue(event.payload, Map::class.java)
        assertEquals(
            setOf("reason"),
            payload.keys,
            "UNAVAILABLE payload must contain exactly these keys — no subject identity in clear",
        )
        assertEquals("registry download failed: HTTP 503", payload["reason"])
    }

    // ---- No-deadlock guarantee ---------------------------------------------------------------

    @Test
    fun `CLEAR followed by HIT in the same logical flow does not deadlock`() {
        val subjectId = UUID.randomUUID()

        // Both methods use REQUIRES_NEW: each takes the write lock, commits, and releases it
        // before the next call. If CLEAR were REQUIRED the lock would be held across both calls,
        // causing HIT's REQUIRES_NEW nested transaction to wait for itself → deadlock.
        val clearEvent = service.appendFreezeScreeningClear(
            ComplianceAuditSubjectType.ASSOCIATION, subjectId, registryDate, 0.85,
        )
        val hitEvent = service.appendFreezeScreeningHit(
            ComplianceAuditSubjectType.ASSOCIATION, subjectId, registryDate, 0.85, 1, 0.91,
        )
        requiresNewRowIds += listOf(clearEvent.id, hitEvent.id)

        assertTrue(hitEvent.sequenceNo > clearEvent.sequenceNo)
    }

    // ---- History query -----------------------------------------------------------------------

    @Test
    fun `findFreezeScreeningHistory returns only screening events for the requested subject`() {
        val targetId = UUID.randomUUID()
        val otherId = UUID.randomUUID()

        val e1 = service.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, targetId, registryDate, 0.85)
        val e2 = service.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, targetId, registryDate, 0.85)
        val e3 = service.appendFreezeScreeningClear(ComplianceAuditSubjectType.DONATION, otherId, registryDate, 0.85)
        requiresNewRowIds += listOf(e1.id, e2.id, e3.id)

        val history = service.findFreezeScreeningHistory(targetId)

        assertEquals(2, history.size, "only the target subject's events must be returned")
        assertTrue(history.all { it.subjectId == targetId })
        assertTrue(history.all { it.eventType == FREEZE_SCREENING_CLEAR })
        // Chronological order (oldest first)
        assertTrue(history[0].sequenceNo < history[1].sequenceNo)
    }

    @Test
    fun `findFreezeScreeningHistory excludes non-screening events for the same subject`() {
        val subjectId = UUID.randomUUID()

        val clearEvent = service.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, subjectId, registryDate, 0.85)
        requiresNewRowIds += clearEvent.id
        // Write a non-screening event using the raw append — must be excluded from history
        service.append(
            eventType = "SCOPE_VERDICT_UNFAVORABLE",
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = subjectId,
            payload = mapOf("verdict" to "OUT_OF_SCOPE"),
        )

        val history = service.findFreezeScreeningHistory(subjectId)

        assertEquals(1, history.size, "non-screening events must not appear in freeze history")
        assertEquals(FREEZE_SCREENING_CLEAR, history.single().eventType)
    }

    // ---- Chain integrity ---------------------------------------------------------------------

    @Test
    fun `successive freeze-screening events are linked by the hash chain`() {
        val subjectId = UUID.randomUUID()

        val e1 = service.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, subjectId, registryDate, 0.85)
        val e2 = service.appendFreezeScreeningClear(ComplianceAuditSubjectType.DONATION, UUID.randomUUID(), registryDate, 0.85)
        val e3 = service.appendFreezeScreeningClear(ComplianceAuditSubjectType.ASSOCIATION, subjectId, null, 0.85)
        requiresNewRowIds += listOf(e1.id, e2.id, e3.id)

        assertEquals(e1.rowHash, e2.prevHash, "e2 must be chained to e1")
        assertEquals(e2.rowHash, e3.prevHash, "e3 must be chained to e2")
    }
}

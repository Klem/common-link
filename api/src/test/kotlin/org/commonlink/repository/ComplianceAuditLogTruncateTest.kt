package org.commonlink.repository

import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.service.ComplianceAuditLogService
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Proves on the real Flyway schema that `compliance_audit_log` refuses TRUNCATE even for a role allowed to run it
 * (audit 2026-11-12, H6).
 *
 * V51 revokes TRUNCATE from the application role, but that role owns the table and can grant it back. The test
 * reproduces exactly that: it re-grants TRUNCATE, then shows the V96 statement-level trigger still refuses.
 *
 * Also runs [ComplianceAuditLogService.verifyChain] — the check now scheduled daily — against rows written through
 * the service on PostgreSQL, whose timestamp rounding the H2 suite cannot reproduce.
 */
class ComplianceAuditLogTruncateTest : RealPostgresTest() {

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var auditLog: ComplianceAuditLogService

    @Test
    fun `a chain written through the service verifies intact on PostgreSQL`() {
        repeat(3) { i ->
            auditLog.append(
                eventType = "TEST_EVENT_$i",
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                payload = mapOf("note" to "chain-$i"),
                subjectId = UUID.randomUUID(),
            )
        }

        assertNull(auditLog.verifyChain())
    }

    @Test
    fun `TRUNCATE is refused by the trigger even after the owner grants it back`() {
        jdbcTemplate.execute("GRANT TRUNCATE ON compliance_audit_log TO CURRENT_USER")

        val refusal = assertThrows<DataAccessException> { jdbcTemplate.execute("TRUNCATE compliance_audit_log") }

        assertTrue(refusal.mostSpecificCause.message!!.contains("append-only")) { refusal.mostSpecificCause.message!! }
    }
}

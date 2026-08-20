package org.commonlink.service

import org.commonlink.repository.ComplianceAuditLogRepository
import org.commonlink.repository.ComplianceDeclarantRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Logic tests for [ComplianceDeclarantService] — four acceptance criteria, H2 in-memory,
 * no Testcontainers/Docker required.
 *
 * The encryption key is set in [TestPropertySource] so [org.commonlink.entity.ComplianceCryptoConverter]
 * operates in real AES-256-GCM mode (not no-op). This lets the encryption test assert that
 * the stored column value carries the `v1:` ciphertext prefix rather than the cleartext number.
 *
 * `compliance_audit_log_seq` and `compliance_audit_log_lock` are non-entity SQL artifacts that
 * exist only in the V51 migration — absent from `create-drop`. [Sql] provisions both from the
 * shared `compliance_audit_log_test_schema.sql` script in `BEFORE_TEST_CLASS` phase so that
 * [org.commonlink.service.ComplianceAuditLogService] can acquire its write lock.
 *
 * The class-level [Transactional] rolls each test method back automatically. The sequence
 * advances non-transactionally on every [org.commonlink.service.ComplianceAuditLogService.append]
 * call, so sequence numbers are not gapless across test methods — this is expected and harmless.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
    "commonlink.compliance.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
])
@Sql(scripts = ["/sql/compliance_audit_log_test_schema.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Transactional
class ComplianceDeclarantServiceTest {

    @Autowired private lateinit var service: ComplianceDeclarantService
    @Autowired private lateinit var declarantRepo: ComplianceDeclarantRepository
    @Autowired private lateinit var auditRepo: ComplianceAuditLogRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val userId = UUID.randomUUID()
    private val teledeclarantNumber = "ERMES-2026-12345"

    @Test
    fun `roundtrip — teledeclarant number and full name are read back correctly via the entity`() {
        val declarant = service.designate(
            userId = userId,
            teledeclarantNumber = teledeclarantNumber,
            fullName = "Marie Dupont",
            designatedAt = LocalDate.of(2026, 1, 15),
        )

        val reloaded = declarantRepo.findById(declarant.id).orElseThrow()
        assertEquals(teledeclarantNumber, reloaded.teledeclarantNumber)
        assertEquals("Marie Dupont", reloaded.fullName)
    }

    @Test
    fun `encryption — raw column value carries the v1 ciphertext prefix, cleartext is never stored`() {
        val declarant = service.designate(
            userId = userId,
            teledeclarantNumber = teledeclarantNumber,
            fullName = "Marie Dupont",
            designatedAt = LocalDate.of(2026, 1, 15),
        )

        val raw = jdbc.queryForObject(
            "SELECT teledeclarant_number FROM compliance_declarant WHERE id = ?",
            String::class.java,
            declarant.id,
        )
        assertTrue(raw!!.startsWith("v1:"), "stored column must carry v1: ciphertext prefix, was: $raw")
        assertFalse(raw.contains(teledeclarantNumber), "cleartext number must not appear in stored ciphertext")
    }

    @Test
    fun `revoke — revoked declarant is absent from the active list but the row is preserved in the database`() {
        val declarant = service.designate(
            userId = userId,
            teledeclarantNumber = teledeclarantNumber,
            fullName = "Marie Dupont",
            designatedAt = LocalDate.of(2026, 1, 15),
        )

        service.revoke(declarantId = declarant.id, revokedAt = LocalDate.of(2026, 8, 6))

        val active = service.listActive()
        assertFalse(active.any { it.id == declarant.id }, "revoked declarant must not appear in the active list")
        assertTrue(declarantRepo.existsById(declarant.id), "revoked row must still exist in the database")
    }

    @Test
    fun `audit log — designate and revoke each produce exactly one journal entry, no payload contains the teledeclarant number`() {
        val declarant = service.designate(
            userId = userId,
            teledeclarantNumber = teledeclarantNumber,
            fullName = "Marie Dupont",
            designatedAt = LocalDate.of(2026, 1, 15),
        )
        service.revoke(declarantId = declarant.id)

        val allEntries = auditRepo.findAll()
        val declarantEntries = allEntries.filter { it.subjectId == declarant.id }
        assertEquals(2, declarantEntries.size, "designate + revoke must each produce exactly one journal entry")

        val eventTypes = declarantEntries.map { it.eventType }.toSet()
        assertTrue("DECLARANT_DESIGNATED" in eventTypes, "missing DECLARANT_DESIGNATED entry")
        assertTrue("DECLARANT_REVOKED" in eventTypes, "missing DECLARANT_REVOKED entry")

        for (entry in allEntries) {
            assertFalse(
                entry.payload.contains(teledeclarantNumber),
                "journal payload must never contain the teledeclarant number (entry: ${entry.eventType})",
            )
        }
    }
}

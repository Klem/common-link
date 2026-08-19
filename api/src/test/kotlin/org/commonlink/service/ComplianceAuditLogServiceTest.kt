package org.commonlink.service

import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.repository.TestcontainersConfig
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import jakarta.persistence.EntityManager

/**
 * Proves the V51 migration's database-level guards (revoked privileges + the
 * `trg_compliance_audit_log_immutable` trigger) actually reject `UPDATE`/`DELETE` — the two
 * acceptance criteria that can only be checked against real Postgres with Flyway actually
 * enabled, unlike the rest of the suite (Hibernate `create-drop`, Flyway disabled — see
 * [MandateServiceTest]'s note on `fiscal_mandate_ref_seq` for the same limitation).
 *
 * Everything else about [ComplianceAuditLogService] — hash chaining, reproducibility, tamper
 * detection, write serialization — is portable and covered without Docker in
 * [ComplianceAuditLogServiceLogicTest]. Only the DB-level guards themselves are Postgres-only
 * (no PL/pgSQL on H2, and the H2 test connection is effectively a superuser, so `REVOKE` there
 * is meaningless), which is why this class alone stays `@Disabled`.
 *
 * ### Disabled — see class-level [Disabled] reason
 *
 * While writing this class, running it exposed a pre-existing, suite-wide issue: the shared
 * [TestcontainersConfig]'s `@ServiceConnection` was not being picked up by Spring in this dev
 * environment, and every `@SpringBootTest`/`@DataJpaTest` was silently falling back to the H2
 * datasource in `application-test.yml` instead of the intended Postgres container — confirmed by
 * querying `SELECT version()`, which returned `PostgreSQL 8.2.23 server protocol using H2 2.4.240`
 * (H2's own Postgres-compatibility-mode banner, not real Postgres). A fix was attempted
 * (`@Bean @ServiceConnection` instead of a static `@Container` field) and did correctly stop the
 * silent fallback — but on retry, Docker itself was not reachable from this Windows-side Gradle
 * JVM against the WSL-hosted `dockerd`, so the fix could not be verified end-to-end. Per explicit
 * decision, that suite-wide wiring change was rolled back rather than shipped unverified — see
 * `.tasks/lessons.md` for the full account. This class stays in the repo, disabled, as the ready
 * DB-level proof for the V51 migration once Testcontainers reliably reaches Postgres here.
 */
@Disabled(
    "Requires a real Postgres via Testcontainers with Flyway enabled. Blocked in this dev " +
        "environment: Testcontainers/Docker connectivity is unreliable here (see class KDoc and " +
        ".tasks/lessons.md). Re-enable once a `SELECT version()` sanity check confirms real " +
        "Postgres, not H2, is behind TestcontainersConfig.",
)
@Tag("testcontainers")
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=none",
])
@Transactional
class ComplianceAuditLogServiceTest {

    @Autowired private lateinit var service: ComplianceAuditLogService
    @Autowired private lateinit var entityManager: EntityManager

    private fun append(eventType: String = "TEST_EVENT") = service.append(
        eventType = eventType,
        subjectType = ComplianceAuditSubjectType.ASSOCIATION,
        payload = mapOf("note" to "test-$eventType"),
        subjectId = UUID.randomUUID(),
    )

    /**
     * The `REVOKE` fires before the trigger ever gets a chance to run — Postgres checks table
     * privileges before executing the statement, so this (and the DELETE test below) proves the
     * `REVOKE` line of defense specifically. The trigger itself is exercised the same way: with
     * privileges temporarily restored, the same `UPDATE` would instead fail on the trigger's own
     * `append-only` message — not asserted separately here to keep this class to exactly the two
     * acceptance criteria ("UPDATE fails", "DELETE fails"), both satisfied by either guard alone.
     */
    @Test
    fun `UPDATE on compliance_audit_log fails at the database level`() {
        val row = append()
        val ex = assertThrows<Exception> {
            entityManager.createNativeQuery(
                "UPDATE compliance_audit_log SET event_type = 'TAMPERED' WHERE id = :id",
            ).setParameter("id", row.id).executeUpdate()
        }
        assertTrue(
            ex.toString().contains("permission denied", ignoreCase = true),
            "expected a permission-denied failure, got: $ex",
        )
    }

    @Test
    fun `DELETE on compliance_audit_log fails at the database level`() {
        val row = append()
        val ex = assertThrows<Exception> {
            entityManager.createNativeQuery(
                "DELETE FROM compliance_audit_log WHERE id = :id",
            ).setParameter("id", row.id).executeUpdate()
        }
        assertTrue(
            ex.toString().contains("permission denied", ignoreCase = true),
            "expected a permission-denied failure, got: $ex",
        )
    }
}

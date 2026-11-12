package org.commonlink.repository

import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertSubjectType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

/**
 * Keeps `compliance_alert_origin_check` and [ComplianceAlertOrigin] identical on the real schema.
 *
 * V72 recreated the constraint without `SCREENING_UNAVAILABLE` and nothing noticed: the suite's H2 schema is generated
 * by Hibernate, without the migrations' CHECK constraints, and the alert caller swallows the failure (audit 2026-11-12,
 * H3). Every origin of the enum is inserted here against the Flyway schema, so a future migration that narrows the
 * list fails this test instead of silently dropping alerts.
 */
class ComplianceAlertOriginCheckTest : RealPostgresTest() {

    @Autowired private lateinit var alertRepository: ComplianceAlertRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `every ComplianceAlertOrigin is accepted by the CHECK constraint`() {
        ComplianceAlertOrigin.entries.forEach { origin ->
            alertRepository.saveAndFlush(
                ComplianceAlert(
                    origin = origin,
                    subjectType = ComplianceAlertSubjectType.SYSTEM,
                    subjectId = UUID.randomUUID(),
                    severity = ComplianceAlertSeverity.MEDIUM,
                    createdAt = Instant.now(),
                ),
            )
        }
    }

    @Test
    fun `the CHECK constraint lists exactly the enum values`() {
        val definition = jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'compliance_alert_origin_check'",
            String::class.java,
        )!!
        val listed = Regex("'([A-Z_]+)'").findAll(definition).map { it.groupValues[1] }.toSet()

        assertTrue(listed == ComplianceAlertOrigin.entries.map { it.name }.toSet()) {
            "CHECK lists $listed, enum has ${ComplianceAlertOrigin.entries}"
        }
    }
}

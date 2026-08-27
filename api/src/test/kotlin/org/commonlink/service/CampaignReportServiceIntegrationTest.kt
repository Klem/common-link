package org.commonlink.service

import jakarta.persistence.EntityManager
import org.commonlink.config.FlywayMigrationTestContainer
import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertStatus
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.ComplianceAlertRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers

import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Regression test for a deadlock in [CampaignReportService.report] that no other test in this
 * suite can catch — see [org.commonlink.service.ComplianceAuditLogService.appendCampaignReported]
 * KDoc for the full mechanism.
 *
 * `report()` writes the `CAMPAIGN_REPORTED` journal entry through its own `REQUIRED` transaction,
 * then calls [ComplianceAlertService.createOrIgnore], which is `REQUIRES_NEW` and writes its own
 * `ALERT_OPENED` entry. Both writes take the same `compliance_audit_log_lock` row lock
 * ([org.commonlink.repository.ComplianceAuditLogRepository.acquireWriteLock]) — a table that
 * exists **only** because Flyway's V51 migration creates it; it has no JPA entity mapping, so
 * Hibernate never creates it under `ddl-auto=create`/`update`.
 *
 * That is precisely why this cannot reuse [org.commonlink.repository.TestcontainersConfig] like
 * every other integration test in this package: `application-test.yml` disables Flyway there and
 * lets Hibernate generate the schema from entities alone (see [FlywayMigrationTest]'s KDoc), so
 * `compliance_audit_log_lock` would never exist and the deadlock this test exists to catch would
 * never be reachable. This mirrors [FlywayMigrationTest] exactly: dedicated empty container, real
 * Flyway migrations, `local` profile.
 *
 * [Timeout] turns a regression into a fast failure instead of a hung build.
 */
@SpringBootTest
@ImportTestcontainers(FlywayMigrationTestContainer::class)
@ActiveProfiles("local")
@Tag("testcontainers")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
])
@Transactional
class CampaignReportServiceIntegrationTest {

    @Autowired private lateinit var campaignReportService: CampaignReportService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var complianceAlertRepository: ComplianceAlertRepository
    @Autowired private lateinit var entityManager: EntityManager

    private lateinit var widgetToken: String
    private lateinit var associationId: UUID

    @BeforeEach
    fun setup() {
        widgetToken = "clk_report_test_${System.nanoTime()}"
        val assocUser = userRepository.save(TestFixtures.associationUser(email = "report-assoc-${System.nanoTime()}@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))

        assoc.widgetToken = widgetToken
        assoc.widgetDestinationCampaign = campaign
        associationProfileRepository.save(assoc)
        associationId = assoc.id!!
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    fun `first report on a fresh association returns instead of deadlocking on the journal lock`() {
        campaignReportService.report(widgetToken, "Contenu problématique", null)
        entityManager.flush()
        entityManager.clear()

        val alert = complianceAlertRepository.findByOriginAndSubjectIdAndStatusIn(
            ComplianceAlertOrigin.CAMPAIGN_REPORT, associationId, listOf(ComplianceAlertStatus.PENDING),
        )
        assertNotNull(alert, "createOrIgnore must have opened a PENDING alert")

        val association = associationProfileRepository.findById(associationId).get()
        assertEquals(AssociationStatus.ALERT, association.status)
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    fun `two reports in a row both return and reuse the same open alert`() {
        campaignReportService.report(widgetToken, "Premier signalement", null)
        campaignReportService.report(widgetToken, "Second signalement", "temoin@example.org")
        entityManager.flush()
        entityManager.clear()

        val alert = complianceAlertRepository.findByOriginAndSubjectIdAndStatusIn(
            ComplianceAlertOrigin.CAMPAIGN_REPORT, associationId, listOf(ComplianceAlertStatus.PENDING),
        )
        assertNotNull(alert, "the dedup index must keep exactly one open alert across both reports")
    }
}

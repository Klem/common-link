package org.commonlink.service

import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.LegalAcceptance
import org.commonlink.entity.LegalAcceptanceSubjectType
import org.commonlink.entity.LegalDocumentType
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.LegalAcceptanceRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Integration tests for [ComplianceAssociationService] — the compliance « Associations »
 * workspace's read side, including the dossier-consultation journal writes
 * (`LCB-FT-compliance-overview.md` §7.2).
 *
 * `compliance_audit_log_lock`/`_seq` are non-entity SQL artifacts from V51, absent from the
 * Hibernate-only schema this suite runs on — see [ComplianceDeclarantServiceTest] KDoc for the
 * same provisioning need.
 */
@Tag("testcontainers")
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@Sql(scripts = ["/sql/compliance_audit_log_test_schema.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@Transactional
class ComplianceAssociationServiceTest {

    @Autowired
    private lateinit var service: ComplianceAssociationService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var associationProfileRepository: AssociationProfileRepository

    @Autowired
    private lateinit var campaignRepository: CampaignRepository

    @Autowired
    private lateinit var auditLogService: ComplianceAuditLogService

    @Autowired
    private lateinit var legalAcceptanceRepository: LegalAcceptanceRepository

    private lateinit var associationId: UUID
    private val officerId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(TestFixtures.associationUser())
        val profile = associationProfileRepository.save(TestFixtures.associationProfile(user))
        associationId = profile.id!!
    }

    @Test
    fun `listAssociations - returns a page sorted by name, no journal write`() {
        val page = service.listAssociations(0, 20)
        assertEquals(1, page.totalElements)
        assertEquals(associationId, page.content.single().id)

        val consultationEntries = auditLogService.findRecentEntries()
            .filter { it.eventType == ComplianceAuditLogService.ASSOCIATION_DOSSIER_CONSULTED && it.subjectId == associationId }
        assertEquals(0, consultationEntries.size, "browsing the index must not itself count as a dossier consultation")
    }

    @Test
    fun `listAssociations - page size is capped server-side regardless of what is requested`() {
        val page = service.listAssociations(0, 999_999)
        assertEquals(ComplianceAssociationService.MAX_PAGE_SIZE, page.size)
    }

    @Test
    fun `getDetail - returns the dossier and writes exactly one ASSOCIATION_DOSSIER_CONSULTED event`() {
        val detail = service.getDetail(associationId, officerId)
        assertEquals(associationId, detail.id)
        assertEquals(AssociationStatus.ACTIVE, detail.status)

        val entries = auditLogService.findRecentEntries()
            .filter { it.eventType == ComplianceAuditLogService.ASSOCIATION_DOSSIER_CONSULTED && it.subjectId == associationId }
        assertEquals(1, entries.size)
        assertEquals(officerId, entries.single().actorUserId)
    }

    @Test
    fun `getDetail - throws NotFoundException for an unknown association`() {
        assertThrows(NotFoundException::class.java) {
            service.getDetail(UUID.randomUUID(), officerId)
        }
    }

    @Test
    fun `listCampaigns - returns every campaign of the association, most recent first`() {
        val association = associationProfileRepository.findById(associationId).get()
        val first = campaignRepository.save(TestFixtures.campaign(association, name = "Campagne A", status = CampaignStatus.DRAFT))
        Thread.sleep(5)
        val second = campaignRepository.save(TestFixtures.campaign(association, name = "Campagne B", status = CampaignStatus.LIVE))

        val campaigns = service.listCampaigns(associationId)
        assertEquals(listOf(second.id, first.id), campaigns.map { it.id })
    }

    @Test
    fun `listCampaigns - throws NotFoundException for an unknown association`() {
        assertThrows(NotFoundException::class.java) {
            service.listCampaigns(UUID.randomUUID())
        }
    }

    @Test
    fun `getCampaignReviewHistory - returns the publish-attempt history and writes exactly one CAMPAIGN_DOSSIER_CONSULTED event`() {
        val association = associationProfileRepository.findById(associationId).get()
        val campaign = campaignRepository.save(TestFixtures.campaign(association))
        val campaignId = campaign.id!!
        auditLogService.appendCampaignReviewRefused(campaignId, associationId, org.commonlink.entity.CampaignReviewRefusalReason.GOAL_MISSING)

        val history = service.getCampaignReviewHistory(campaignId, officerId)
        assertEquals(1, history.size)
        assertEquals(ComplianceAuditLogService.CAMPAIGN_REVIEW_REFUSED, history.single().eventType)

        val consultationEntries = auditLogService.findRecentEntries()
            .filter { it.eventType == ComplianceAuditLogService.CAMPAIGN_DOSSIER_CONSULTED && it.subjectId == campaignId }
        assertEquals(1, consultationEntries.size)
        assertEquals(officerId, consultationEntries.single().actorUserId)
    }

    @Test
    fun `getCampaignReviewHistory - throws NotFoundException for an unknown campaign`() {
        assertThrows(NotFoundException::class.java) {
            service.getCampaignReviewHistory(UUID.randomUUID(), officerId)
        }
    }

    @Test
    fun `getCampaignDonorAcceptances - groups the campaign's donor rows by donor, writes no consultation event`() {
        val association = associationProfileRepository.findById(associationId).get()
        val campaign = campaignRepository.save(TestFixtures.campaign(association))
        val campaignId = campaign.id!!
        val donorId = UUID.randomUUID()
        legalAcceptanceRepository.save(
            LegalAcceptance(
                subjectType = LegalAcceptanceSubjectType.DONOR, subjectId = donorId,
                documentType = LegalDocumentType.CGU, documentVersion = "2026-08-26",
                signerName = "Marie Curie", signerEmail = "marie@example.org", campaignId = campaignId,
            )
        )
        legalAcceptanceRepository.save(
            LegalAcceptance(
                subjectType = LegalAcceptanceSubjectType.DONOR, subjectId = donorId,
                documentType = LegalDocumentType.CGV, documentVersion = "2026-08-26",
                signerName = "Marie Curie", signerEmail = "marie@example.org", campaignId = campaignId,
            )
        )

        val groups = service.getCampaignDonorAcceptances(campaignId)

        assertEquals(1, groups.size, "both rows belong to the same donor")
        assertEquals(donorId, groups.single().donorId)
        assertEquals(2, groups.single().acceptances.size)

        val consultationEntries = auditLogService.findRecentEntries()
            .filter { it.eventType == ComplianceAuditLogService.CAMPAIGN_DOSSIER_CONSULTED && it.subjectId == campaignId }
        assertEquals(0, consultationEntries.size, "reading donor acceptances is a sub-resource of the already-opened dossier")
    }

    @Test
    fun `getCampaignDonorAcceptances - throws NotFoundException for an unknown campaign`() {
        assertThrows(NotFoundException::class.java) {
            service.getCampaignDonorAcceptances(UUID.randomUUID())
        }
    }
}

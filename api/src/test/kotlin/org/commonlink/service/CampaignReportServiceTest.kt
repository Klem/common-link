package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** Unit tests for [CampaignReportService] — the public report intake (IC-44). */
class CampaignReportServiceTest {

    private val associationProfileRepository: AssociationProfileRepository = mockk()
    private val auditLog: ComplianceAuditLogService = mockk()
    private val complianceAlertService: ComplianceAlertService = mockk(relaxed = true)
    private val associationComplianceStatusService: AssociationComplianceStatusService = mockk(relaxed = true)

    private val service = CampaignReportService(
        associationProfileRepository, auditLog, complianceAlertService, associationComplianceStatusService,
    )

    private val associationId: UUID = UUID.randomUUID()
    private val campaignId: UUID = UUID.randomUUID()

    private fun campaign() = Campaign(
        id = campaignId,
        association = mockk(relaxed = true),
        name = "Campagne test",
        goal = BigDecimal("1000.00"),
    )

    private fun association(campaign: Campaign?) = AssociationProfile(
        id = associationId,
        user = User(email = "asso@example.org", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL),
        name = "Association test",
        identifier = "W123456789",
        widgetDestinationCampaign = campaign,
        status = AssociationStatus.ACTIVE,
    )

    @Test
    fun `report throws NotFoundException for an unknown widget token`() {
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.empty()
        assertThrows<NotFoundException> { service.report("clk_x", "Ce projet ne respecte pas les critères", null) }
    }

    @Test
    fun `report throws NotFoundException when no destination campaign is configured`() {
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association(null))
        assertThrows<NotFoundException> { service.report("clk_x", "Ce projet ne respecte pas les critères", null) }
    }

    @Test
    fun `report writes one journal entry via the REQUIRES_NEW helper, opens an alert, and raises the association to ALERT`() {
        val campaign = campaign()
        every { associationProfileRepository.findByWidgetToken("clk_x") } returns Optional.of(association(campaign))

        val entry = ComplianceAuditLog(
            sequenceNo = 42L,
            eventType = ComplianceAuditLogService.CAMPAIGN_REPORTED,
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = associationId,
            payload = "{}",
            occurredAt = Instant.now(),
            rowHash = "hash",
        )
        // Must go through the dedicated REQUIRES_NEW helper, never the bare append() — see
        // ComplianceAuditLogService.appendCampaignReported KDoc: append() under this method's own
        // REQUIRED transaction would deadlock against ComplianceAlertService.createOrIgnore's own
        // REQUIRES_NEW write to the same compliance_audit_log_lock row.
        every {
            auditLog.appendCampaignReported(
                associationId = eq(associationId),
                campaignId = eq(campaignId),
                message = eq("Ce projet ne respecte pas les critères"),
                reporterEmail = eq("temoin@example.org"),
            )
        } returns entry

        service.report("clk_x", "Ce projet ne respecte pas les critères", "temoin@example.org")

        verify(exactly = 1) {
            auditLog.appendCampaignReported(associationId, campaignId, "Ce projet ne respecte pas les critères", "temoin@example.org")
        }
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
        verify(exactly = 1) {
            complianceAlertService.createOrIgnore(
                origin = eq(ComplianceAlertOrigin.CAMPAIGN_REPORT),
                subjectType = eq(ComplianceAlertSubjectType.ASSOCIATION),
                subjectId = eq(associationId),
                severity = eq(ComplianceAlertSeverity.MEDIUM),
                auditLogSeqRef = eq(42L),
            )
        }
        verify(exactly = 1) { associationComplianceStatusService.raiseAlert(associationId) }
    }
}

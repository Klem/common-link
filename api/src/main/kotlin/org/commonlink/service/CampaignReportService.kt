package org.commonlink.service

import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Public campaign-report intake (IC-44 — canal de signalement de campagne).
 *
 * A report is always resolved through the association's widget token — there is no public
 * campaign id lookup elsewhere in the API, so this mirrors [PublicWidgetService]'s own
 * resolution rather than introducing a second public identifier space. Reporting is allowed
 * regardless of the destination campaign's [org.commonlink.entity.CampaignStatus] (an ended
 * campaign can still be reported), unlike donation creation.
 *
 * Deliberately not part of [PublicWidgetService]: this concern is about compliance intake, not
 * donation orchestration.
 */
@Service
class CampaignReportService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val auditLog: ComplianceAuditLogService,
    private val complianceAlertService: ComplianceAlertService,
    private val associationComplianceStatusService: AssociationComplianceStatusService,
) {
    /**
     * Records a report against the widget's destination campaign.
     *
     * Writes one [ComplianceAuditLogService.CAMPAIGN_REPORTED] journal entry per call — never
     * merged or overwritten, so a second report received while the first alert is still open is
     * not lost (see [ComplianceAuditLogService.findCampaignReportHistory]). Opens (or reuses) a
     * [ComplianceAlertOrigin.CAMPAIGN_REPORT] alert and raises the association to `ALERT` — a
     * no-op if it is already `ALERT` or `SUSPENDED`.
     *
     * Severity is [ComplianceAlertSeverity.MEDIUM]: an anonymous, unverified report is not the
     * same evidentiary weight as an automated sanctions-list hit.
     *
     * The journal write goes through [ComplianceAuditLogService.appendCampaignReported]
     * (`REQUIRES_NEW`), never the bare `append()` — see that method's KDoc: writing under this
     * method's own `REQUIRED` transaction would hold the audit-log row lock while the
     * `REQUIRES_NEW` call below tries to acquire the same lock, deadlocking every first report.
     */
    @Transactional
    fun report(widgetToken: String, message: String, reporterEmail: String?) {
        val association = associationProfileRepository.findByWidgetToken(widgetToken)
            .orElseThrow { NotFoundException("Widget not found") }
        val campaign = association.widgetDestinationCampaign
            ?: throw NotFoundException("No destination campaign configured")
        val associationId = association.id!!

        val entry = auditLog.appendCampaignReported(
            associationId = associationId,
            campaignId = campaign.id!!,
            message = message,
            reporterEmail = reporterEmail,
        )

        complianceAlertService.createOrIgnore(
            origin = ComplianceAlertOrigin.CAMPAIGN_REPORT,
            subjectType = ComplianceAlertSubjectType.ASSOCIATION,
            subjectId = associationId,
            severity = ComplianceAlertSeverity.MEDIUM,
            auditLogSeqRef = entry.sequenceNo,
        )

        associationComplianceStatusService.raiseAlert(associationId)
    }
}

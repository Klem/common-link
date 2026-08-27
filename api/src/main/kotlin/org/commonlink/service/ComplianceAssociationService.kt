package org.commonlink.service

import org.commonlink.dto.AuditLogEntryDto
import org.commonlink.dto.CampaignSummaryDto
import org.commonlink.dto.ComplianceAssociationDetailDto
import org.commonlink.dto.ComplianceAssociationSummaryDto
import org.commonlink.dto.DonorLegalAcceptanceGroupDto
import org.commonlink.dto.toComplianceDetailDto
import org.commonlink.dto.toComplianceSummaryDto
import org.commonlink.dto.toEntryDto
import org.commonlink.dto.toSummaryDto
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read-side of the compliance « Associations » workspace — browse every association's dossier,
 * independent of whether it has an open alert or a registry scan on file. Every route this
 * service backs is COMPLIANCE_OFFICER-only at [org.commonlink.security.SecurityConfig] (every
 * route under `/api/compliance/`); no per-method authorization check is needed here.
 */
@Service
class ComplianceAssociationService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val campaignRepository: CampaignRepository,
    private val auditLog: ComplianceAuditLogService,
    private val legalAcceptanceService: LegalAcceptanceService,
) {
    companion object {
        /** Hard ceiling on page size, independent of what the caller requests (rule 8 — server never trusts the client's bound). */
        const val MAX_PAGE_SIZE = 100
    }

    /** Paginated association index, sorted by name — plain reading of "displays all the associations". */
    @Transactional(readOnly = true)
    fun listAssociations(page: Int, size: Int): Page<ComplianceAssociationSummaryDto> {
        val pageable: Pageable = PageRequest.of(page, size.coerceIn(1, MAX_PAGE_SIZE), Sort.by(Sort.Direction.ASC, "name"))
        return associationProfileRepository.findAll(pageable).map { it.toComplianceSummaryDto() }
    }

    /**
     * Full dossier of one association. Writes [ComplianceAuditLogService.appendAssociationDossierConsulted]
     * once per call — the officer opening this dossier is itself a fact the LCB-FT overview names
     * as needing a durable, queryable trace (§7.2), distinct from the application-log line
     * [org.commonlink.security.ComplianceAccessLogFilter] already writes for every compliance
     * request. Sub-resources of the same page (campaigns, CGU/CGV history) do not write their own
     * consultation event — they are read as part of this one dossier view, not a separate one.
     *
     * @throws NotFoundException if no association exists with this id.
     */
    @Transactional
    fun getDetail(associationId: UUID, actorUserId: UUID): ComplianceAssociationDetailDto {
        val association = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId introuvable") }
        auditLog.appendAssociationDossierConsulted(associationId, actorUserId)
        return association.toComplianceDetailDto()
    }

    /**
     * Every campaign of the association, most recent first. Not a dossier consultation in its own
     * right (see [getDetail] KDoc) — no journal write here.
     *
     * @throws NotFoundException if no association exists with this id.
     */
    @Transactional(readOnly = true)
    fun listCampaigns(associationId: UUID): List<CampaignSummaryDto> {
        if (!associationProfileRepository.existsById(associationId)) {
            throw NotFoundException("Association $associationId introuvable")
        }
        return campaignRepository.findAllWithMilestonesByAssociationIdOrderByCreatedAtDesc(associationId)
            .map { it.toSummaryDto() }
    }

    /**
     * A single campaign's publish-attempt history — [ComplianceAuditLogService.CAMPAIGN_REVIEW_RETAINED]
     * / [ComplianceAuditLogService.CAMPAIGN_REVIEW_REFUSED] events, oldest first. **Not** a general
     * campaign status history: `preparePublish()` is the only gate this journal traces, so a
     * LIVE→PAUSED→LIVE cycle after publication leaves no entry here — see
     * `docs/legal/E6-tracage-refus-metriques-rapport-annuel.md` §6.
     *
     * Writes [ComplianceAuditLogService.appendCampaignDossierConsulted] once per call, same
     * rationale as [getDetail].
     *
     * @throws NotFoundException if no campaign exists with this id.
     */
    @Transactional
    fun getCampaignReviewHistory(campaignId: UUID, actorUserId: UUID): List<AuditLogEntryDto> {
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign $campaignId introuvable") }
        auditLog.appendCampaignDossierConsulted(campaignId, campaign.association.id!!, actorUserId)
        return auditLog.findCampaignReviewHistory(campaignId).map { it.toEntryDto() }
    }

    /**
     * Donor CGU/CGV acceptance proof for this campaign, grouped by donor (see
     * [LegalAcceptanceService.donorAcceptancesForCampaign]). Not itself a dossier consultation — read
     * as part of the campaign dossier already opened by [getCampaignReviewHistory], same rationale as
     * [listCampaigns].
     *
     * @throws NotFoundException if no campaign exists with this id.
     */
    @Transactional(readOnly = true)
    fun getCampaignDonorAcceptances(campaignId: UUID): List<DonorLegalAcceptanceGroupDto> {
        if (!campaignRepository.existsById(campaignId)) {
            throw NotFoundException("Campaign $campaignId introuvable")
        }
        return legalAcceptanceService.donorAcceptancesForCampaign(campaignId)
    }
}

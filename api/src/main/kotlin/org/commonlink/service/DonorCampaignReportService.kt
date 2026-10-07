package org.commonlink.service

import org.commonlink.dto.CampaignPayoutLineDto
import org.commonlink.dto.DonorCampaignReportDto
import org.commonlink.dto.toDto
import org.commonlink.entity.PayoutStatus
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Static, validated wording (D2, project rule 11) for the on-chain registry banner shown on the
 * donor-facing campaign report. Never build this string ad hoc elsewhere — the state it describes
 * ("dépenses en cours de déploiement") is real only until `recordPayout` is deployed (CD4).
 */
const val REGISTRY_BANNER_TEXT =
    "Les dons sont inscrits dans un registre public. Les dépenses sont tracées et vérifiées ; " +
        "leur inscription au registre public est en cours de déploiement."

/**
 * Assembles the donor-facing "bilan de campagne" page: hero data, the donor's own contribution,
 * milestones, confirmed payouts, and budget variance (prévu vs dépensé).
 */
@Service
class DonorCampaignReportService(
    private val donorReadScope: DonorReadScope,
    private val campaignRepository: CampaignRepository,
    private val campaignMilestoneRepository: CampaignMilestoneRepository,
    private val payoutRepository: PayoutRepository,
    private val donationRepository: DonationRepository,
    private val reportingService: ReportingService,
    private val campaignStoryService: CampaignStoryService,
) {

    /**
     * @param userId UUID of the authenticated user.
     * @throws NotFoundException if [campaignId] does not exist.
     * @throws org.springframework.security.access.AccessDeniedException if the donor has no
     *   confirmed donation on [campaignId].
     */
    @Transactional(readOnly = true)
    fun getReport(userId: UUID, campaignId: UUID): DonorCampaignReportDto {
        val donorId = donorReadScope.resolve(userId).id!!
        donorReadScope.assertHasDonatedTo(donorId, campaignId)
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }

        val confirmedPayouts = payoutRepository
            .findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED)
            .map {
                CampaignPayoutLineDto(
                    payoutId = it.id,
                    label = it.label,
                    amount = it.amount,
                    payeeName = it.payee.name,
                    confirmedAt = it.confirmedAt!!,
                    sectionCode = it.typeCode.substringBefore('-'),
                )
            }

        return DonorCampaignReportDto(
            campaignId = campaignId,
            campaignName = campaign.name,
            campaignEmoji = campaign.emoji,
            associationName = campaign.association.name,
            status = campaign.status,
            goal = campaign.goal,
            raised = campaign.raised,
            // Never a share of other donors' contributions — this donor's own total only.
            donorContribution = donationRepository
                .sumConfirmedAmountByDonorIdAndCampaignId(donorId, campaignId) ?: BigDecimal.ZERO,
            milestones = campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaignId).map { it.toDto() },
            confirmedPayouts = confirmedPayouts,
            variance = reportingService.getVarianceForDonor(campaignId, donorId),
            registryBannerText = REGISTRY_BANNER_TEXT,
            story = campaignStoryService.getPublishedStory(campaignId),
        )
    }
}

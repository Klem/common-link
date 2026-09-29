package org.commonlink.dto

import org.commonlink.entity.CampaignStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Donor-facing "bilan de campagne": hero data, the donor's own contribution, milestones,
 * confirmed payouts, and budget variance (prévu vs dépensé).
 *
 * @property donorContribution this donor's own total confirmed amount on this campaign — never a
 *   share of other donors' contributions.
 * @property registryBannerText static, validated wording (D2) — never built ad hoc in the frontend.
 */
data class DonorCampaignReportDto(
    val campaignId: UUID,
    val campaignName: String,
    val campaignEmoji: String,
    val associationName: String,
    val status: CampaignStatus,
    val goal: BigDecimal,
    val raised: BigDecimal,
    val donorContribution: BigDecimal,
    val milestones: List<MilestoneDto>,
    val confirmedPayouts: List<CampaignPayoutLineDto>,
    val variance: BudgetVarianceDto,
    val registryBannerText: String,
)

/**
 * One confirmed payout of the campaign, as shown in the donor's "bilan" fund-use detail.
 *
 * @property sectionCode budget section code this payout belongs to, for grouping under
 *   [DonorCampaignReportDto.variance]'s per-category disclosure — same prefix convention as
 *   [ReportingService.buildActualMap] ("60-mat" → "60").
 */
data class CampaignPayoutLineDto(
    val payoutId: UUID,
    val label: String,
    val amount: BigDecimal,
    val payeeName: String,
    val confirmedAt: Instant,
    val sectionCode: String,
)

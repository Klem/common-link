package org.commonlink.service

import org.commonlink.dto.DonorAssociationDto
import org.commonlink.dto.DonorCampaignStatus
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.CampaignStatus
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read-only service backing the donor's "My associations" page.
 *
 * Lists the associations the donor has actually funded, with what they gave, how many campaigns
 * they supported, and the association's published-payout record. Resolution goes through
 * [DonorReadScope]: the page takes no association id, so nothing outside the donor's own history
 * can be reached.
 */
@Service
class DonorAssociationService(
    private val donorReadScope: DonorReadScope,
    private val donationRepository: DonationRepository,
    private val payoutRepository: PayoutRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val publicCampaignDirectoryService: PublicCampaignDirectoryService,
) {

    /**
     * Returns the associations the donor supports, most funded first.
     *
     * @param userId UUID of the authenticated user.
     * @return one entry per association with at least one confirmed donation; empty when the donor
     *   has never given.
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     */
    @Transactional(readOnly = true)
    fun listAssociations(userId: UUID): List<DonorAssociationDto> {
        val donorId = donorReadScope.resolve(userId).id!!
        val aggregates = donationRepository.findAssociationAggregatesByDonorId(donorId)
        if (aggregates.isEmpty()) return emptyList()

        // Rows come oldest first, so the last one seen per association is the category of the
        // campaign funded most recently — see findAssociationCategoriesByDonorId.
        val categories: Map<UUID, String?> = donationRepository.findAssociationCategoriesByDonorId(donorId)
            .associate { it.getAssociationId() to it.getCategory() }

        return aggregates.map { row ->
            val association = associationProfileRepository.findById(row.getAssociationId()).orElse(null)
            DonorAssociationDto(
                associationId = row.getAssociationId(),
                name = row.getName(),
                category = categories[row.getAssociationId()],
                totalDonated = row.getTotalAmount(),
                // One count per association: bounded by the number of associations this donor funds.
                publishedPayoutCount = payoutRepository.countConfirmedByAssociationId(row.getAssociationId()),
                campaignCount = row.getCampaignCount().toInt(),
                lastDonationAt = row.getLastDonationAt(),
                donationUrl = resolveDonationUrl(association),
                campaignStatus = resolveCampaignStatus(association),
                campaignId = association?.widgetDestinationCampaign?.id,
                campaignName = association?.widgetDestinationCampaign?.name,
            )
        }
    }

    /**
     * Absolute donation URL for [associationId], or null when its widget isn't currently reachable
     * (see [org.commonlink.entity.AssociationProfile.hasEligibleWidget]). One lookup per row: bounded
     * by the number of associations a single donor funds (same N+1 tolerance as sprints 2-3).
     */
    private fun resolveDonationUrl(association: AssociationProfile?): String? {
        if (association == null) return null
        if (!association.hasEligibleWidget()) return null
        return publicCampaignDirectoryService.buildDonationUrl(association.widgetToken!!)
    }

    /**
     * Coarse campaign-activity signal for the "freshness-tag" (sprint 5, L18) — derived from the
     * same [AssociationProfile] already loaded for [resolveDonationUrl], no new query.
     */
    private fun resolveCampaignStatus(association: AssociationProfile?): DonorCampaignStatus =
        when (association?.widgetDestinationCampaign?.status) {
            CampaignStatus.LIVE -> DonorCampaignStatus.LIVE
            CampaignStatus.COMPLETED -> DonorCampaignStatus.COMPLETED
            else -> DonorCampaignStatus.NONE
        }
}

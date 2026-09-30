package org.commonlink.service

import org.commonlink.dto.DonorFeedItemDto
import org.commonlink.dto.DonorFeedItemType
import org.commonlink.entity.DonorProfile
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Read-only service backing the donor's "Depuis votre dernière visite" home block (L15, restricted
 * to the in-app feed -- the monthly email is chantier CD6, see the sprint 4 spec).
 *
 * Events are computed on the fly from three existing sources -- no materialized feed table (same
 * principle as [DonationAllocationService]'s FIFO: volume is bounded to the campaigns a single
 * donor supports).
 *
 * Every event mentioning a payout uses the D2-validated wording -- never "inscrit au registre
 * public" -- since [org.commonlink.service.OnchainRegistryClient] `recordPayout` remains a stub
 * (see `docs/legal/registre-onchain-des-depenses.md`).
 */
@Service
class DonorEngagementService(
    private val donorReadScope: DonorReadScope,
    private val donorProfileRepository: DonorProfileRepository,
    private val payoutRepository: PayoutRepository,
    private val campaignMilestoneRepository: CampaignMilestoneRepository,
    private val campaignRepository: CampaignRepository,
) {

    /**
     * Events since the donor's last visit, newest first, capped at [MAX_ITEMS]. Read-only: does
     * **not** update [DonorProfile.lastSeenAt] -- see [markSeen]. On a donor's very first call
     * (`lastSeenAt` null), returns everything available rather than nothing, so a brand-new donor
     * isn't shown an empty "what's new" block.
     *
     * Filtered by the donor's own notification preferences: [DonorProfile.notifyNewPayout] gates
     * [DonorFeedItemType.PAYOUT_CONFIRMED]; [DonorProfile.notifyGoalReached] gates
     * [DonorFeedItemType.MILESTONE_REACHED] and [DonorFeedItemType.CAMPAIGN_COMPLETED].
     *
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     */
    @Transactional(readOnly = true)
    fun getFeed(userId: UUID): List<DonorFeedItemDto> {
        val donor = donorReadScope.resolve(userId)
        val donorId = donor.id!!
        // Repository queries take a non-null threshold -- see their KDoc for why a nullable
        // parameter breaks on PostgreSQL. EPOCH means "everything available" for a first visit.
        val since = donor.lastSeenAt ?: Instant.EPOCH

        val items = mutableListOf<DonorFeedItemDto>()

        if (donor.notifyNewPayout) {
            payoutRepository.findConfirmedSinceForDonor(donorId, since).forEach { payout ->
                items += DonorFeedItemDto(
                    type = DonorFeedItemType.PAYOUT_CONFIRMED,
                    campaignId = payout.campaign.id!!,
                    campaignName = payout.campaign.name,
                    associationName = payout.campaign.association.name,
                    occurredAt = payout.confirmedAt!!,
                    label = "${payout.campaign.association.name} a confirmé une nouvelle dépense pour " +
                        "${payout.campaign.name}. Les dépenses sont tracées et vérifiées ; leur " +
                        "inscription au registre public est en cours de déploiement.",
                )
            }
        }

        if (donor.notifyGoalReached) {
            campaignMilestoneRepository.findReachedSinceForDonor(donorId, since).forEach { milestone ->
                items += DonorFeedItemDto(
                    type = DonorFeedItemType.MILESTONE_REACHED,
                    campaignId = milestone.campaign.id!!,
                    campaignName = milestone.campaign.name,
                    associationName = milestone.campaign.association.name,
                    occurredAt = milestone.reachedAt!!,
                    label = "${milestone.campaign.name} a atteint le palier « ${milestone.title} ».",
                )
            }
            campaignRepository.findCompletedSinceForDonor(donorId, since).forEach { campaign ->
                items += DonorFeedItemDto(
                    type = DonorFeedItemType.CAMPAIGN_COMPLETED,
                    campaignId = campaign.id!!,
                    campaignName = campaign.name,
                    associationName = campaign.association.name,
                    occurredAt = campaign.updatedAt,
                    label = "${campaign.name} a atteint son objectif et est désormais clôturée.",
                )
            }
        }

        return items.sortedByDescending { it.occurredAt }.take(MAX_ITEMS)
    }

    /**
     * Marks the feed as seen: sets [DonorProfile.lastSeenAt] to now. Called explicitly by the
     * front-end once the donor has actually viewed the block -- never as a side effect of
     * [getFeed], so a page reload before the donor reads it doesn't silently clear the block.
     *
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     */
    @Transactional
    fun markSeen(userId: UUID) {
        val donor = donorReadScope.resolve(userId)
        donor.lastSeenAt = Instant.now()
        donorProfileRepository.save(donor)
    }

    private companion object {
        const val MAX_ITEMS = 20
    }
}

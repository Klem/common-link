package org.commonlink.repository

import org.commonlink.entity.CampaignMilestone
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface CampaignMilestoneRepository : JpaRepository<CampaignMilestone, UUID> {

    /**
     * Returns all milestones for the given campaign, sorted by [CampaignMilestone.sortOrder] ascending.
     *
     * @param campaignId the UUID of the [org.commonlink.entity.Campaign]
     */
    fun findAllByCampaignIdOrderBySortOrder(campaignId: UUID): List<CampaignMilestone>

    /**
     * Finds a milestone by its own ID and the owning campaign's ID.
     *
     * Used to verify ownership before returning or mutating data — prevents cross-campaign access.
     *
     * @param id the UUID of the [CampaignMilestone]
     * @param campaignId the UUID of the [org.commonlink.entity.Campaign]
     */
    fun findByIdAndCampaignId(id: UUID, campaignId: UUID): Optional<CampaignMilestone>

    /**
     * Returns the milestone closest to its target (smallest positive remaining amount)
     * across all LIVE campaigns for the given association, excluding REACHED milestones.
     *
     * Campaign is JOIN FETCHed to allow the caller to access [campaign.raised] without lazy-loading.
     * Use `PageRequest.of(0, 1)` to retrieve only the single next milestone.
     *
     * @param associationId UUID of the [org.commonlink.entity.AssociationProfile]
     */
    @Query("""
        SELECT m FROM CampaignMilestone m
        JOIN FETCH m.campaign c
        WHERE c.association.id = :associationId
          AND c.status = org.commonlink.entity.CampaignStatus.LIVE
          AND m.status <> org.commonlink.entity.MilestoneStatus.REACHED
          AND m.targetAmount > c.raised
        ORDER BY (m.targetAmount - c.raised) ASC
    """)
    fun findNextMilestoneByAssociationId(
        @Param("associationId") associationId: UUID,
        pageable: Pageable,
    ): List<CampaignMilestone>
}

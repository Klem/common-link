package org.commonlink.repository

import org.commonlink.entity.CampaignMilestone
import org.springframework.data.jpa.repository.JpaRepository
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
}

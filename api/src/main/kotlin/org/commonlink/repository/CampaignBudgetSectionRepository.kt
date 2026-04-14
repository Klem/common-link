package org.commonlink.repository

import org.commonlink.entity.CampaignBudgetSection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CampaignBudgetSectionRepository : JpaRepository<CampaignBudgetSection, UUID> {

    /**
     * Deletes all budget sections belonging to the given campaign.
     *
     * Used when replacing the entire budget prévisionnel in a single update operation.
     *
     * @param campaignId the UUID of the [org.commonlink.entity.Campaign]
     */
    fun deleteAllByCampaignId(campaignId: UUID)
}

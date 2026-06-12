package org.commonlink.repository

import org.commonlink.entity.CampaignBudgetSection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface CampaignBudgetSectionRepository : JpaRepository<CampaignBudgetSection, UUID> {

    /**
     * Deletes all budget sections belonging to the given campaign in a single bulk statement.
     *
     * Child [org.commonlink.entity.CampaignBudgetItem] rows are removed by the DB-level
     * `ON DELETE CASCADE` on `campaign_budget_items.section_id` (V9) — no JPA cascade needed.
     *
     * `flushAutomatically` commits pending session writes before the DELETE so in-memory
     * additions are not lost. `clearAutomatically` evicts stale section/item proxies from
     * the first-level cache after the DELETE, preventing phantom reads mid-transaction.
     *
     * @param campaignId the UUID of the [org.commonlink.entity.Campaign]
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CampaignBudgetSection s WHERE s.campaign.id = :campaignId")
    @Transactional
    fun deleteAllByCampaignId(@Param("campaignId") campaignId: UUID)
}

package org.commonlink.repository

import org.commonlink.entity.Campaign
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface CampaignRepository : JpaRepository<Campaign, UUID> {

    /**
     * Returns all campaigns belonging to the given association.
     *
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     */
    fun findAllByAssociationId(associationId: UUID): List<Campaign>

    /**
     * Returns all campaigns for the given association, eagerly fetching milestones.
     *
     * Used in list views that need [org.commonlink.entity.CampaignMilestone] count without
     * triggering lazy-loading outside a transaction.
     *
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     */
    @EntityGraph(attributePaths = ["milestones"])
    fun findAllWithMilestonesByAssociationId(associationId: UUID): List<Campaign>

    /**
     * Finds a campaign by its own ID and the owning association's ID.
     *
     * Used to verify ownership before returning or mutating data — prevents cross-association access.
     *
     * @param id the UUID of the [Campaign]
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     */
    fun findByIdAndAssociationId(id: UUID, associationId: UUID): Optional<Campaign>

    /**
     * Finds a campaign by its id and association id, suitable for detail views.
     *
     * Loading strategy (3 bounded queries, no N+1):
     * 1. Main SELECT JOIN FETCHes [org.commonlink.entity.CampaignMilestone] (milestones is a Set —
     *    no MultipleBagFetchException).
     * 2. [org.commonlink.entity.CampaignBudgetSection] initialises lazily (1 query).
     * 3. [org.commonlink.entity.CampaignBudgetSection.items] loads via @BatchSize(20) —
     *    all sections' items in one IN (...) query instead of N per-section queries.
     *
     * @param id the UUID of the [Campaign]
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     */
    @EntityGraph(attributePaths = ["milestones"])
    fun findWithDetailsByIdAndAssociationId(id: UUID, associationId: UUID): Optional<Campaign>
}

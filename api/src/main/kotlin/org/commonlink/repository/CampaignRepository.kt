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
     * Collections ([org.commonlink.entity.CampaignBudgetSection], their items, and
     * [org.commonlink.entity.CampaignMilestone]) are lazy — they will be initialized on
     * access within an open Hibernate session. Callers must be inside a `@Transactional`
     * scope (e.g. [org.commonlink.service.CampaignService.getCampaign] is annotated
     * `@Transactional(readOnly = true)`) before calling [Campaign.toDto].
     *
     * Note: Hibernate 6 raises [org.hibernate.loader.MultipleBagFetchException] when
     * attempting to JOIN FETCH more than one bag (List) collection simultaneously, which is
     * why this method intentionally avoids any `@EntityGraph` annotation.
     *
     * @param id the UUID of the [Campaign]
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     */
    fun findWithDetailsByIdAndAssociationId(id: UUID, associationId: UUID): Optional<Campaign>
}

package org.commonlink.repository

import org.commonlink.entity.Campaign
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
     * Finds a campaign by its own ID and the owning association's ID.
     *
     * Used to verify ownership before returning or mutating data — prevents cross-association access.
     *
     * @param id the UUID of the [Campaign]
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     */
    fun findByIdAndAssociationId(id: UUID, associationId: UUID): Optional<Campaign>
}

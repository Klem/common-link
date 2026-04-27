package org.commonlink.repository

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MoneriumConnection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MoneriumConnectionRepository : JpaRepository<MoneriumConnection, UUID> {

    /**
     * Returns the Monerium connection for the given association, or null if none exists.
     *
     * @param association the [AssociationProfile] to look up
     */
    fun findByAssociation(association: AssociationProfile): MoneriumConnection?
}

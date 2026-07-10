package org.commonlink.repository

import org.commonlink.entity.AssociationRegistryCheck
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Append-only store of automated registry pre-check runs.
 *
 * New scans are inserted, never updated. The most recent row for an association
 * is its current "last verified" state.
 */
interface AssociationRegistryCheckRepository : JpaRepository<AssociationRegistryCheck, UUID> {

    /** Returns the most recent scan for the association, or null if it was never scanned. */
    fun findTopByAssociationIdOrderByCheckedAtDesc(associationId: UUID): AssociationRegistryCheck?
}

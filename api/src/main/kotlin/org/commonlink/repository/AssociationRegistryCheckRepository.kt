package org.commonlink.repository

import org.commonlink.entity.AssociationRegistryCheck
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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

    /**
     * Returns association UUIDs that have at least one scan, ordered so the association with the
     * most recent scan comes first. Used by the compliance dashboard to page over all scanned
     * associations without duplicates.
     */
    @Query("SELECT r.associationId FROM AssociationRegistryCheck r GROUP BY r.associationId ORDER BY MAX(r.checkedAt) DESC")
    fun findAssociationIdsWithScansOrderedByLatest(): List<UUID>
}

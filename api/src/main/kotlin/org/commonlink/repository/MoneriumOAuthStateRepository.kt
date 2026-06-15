package org.commonlink.repository

import org.commonlink.entity.MoneriumOAuthState
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MoneriumOAuthStateRepository : JpaRepository<MoneriumOAuthState, String> {

    /**
     * Deletes all expired state records older than [threshold].
     *
     * Should be called periodically to prevent unbounded table growth from abandoned flows.
     *
     * @param threshold cutoff instant; records with [MoneriumOAuthState.expiresAt] before
     *   this value are removed
     */
    fun deleteAllByExpiresAtBefore(threshold: Instant)

    /**
     * Returns true if the given association has at least one non-expired OAuth state record,
     * indicating that an authorization flow was initiated but not yet completed.
     *
     * @param associationId UUID of the association whose pending states are checked
     * @param threshold records must expire after this instant to be considered active
     */
    fun existsByAssociationIdAndExpiresAtAfter(associationId: UUID, threshold: Instant): Boolean
}

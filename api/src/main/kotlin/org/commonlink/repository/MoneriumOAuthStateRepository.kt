package org.commonlink.repository

import org.commonlink.entity.MoneriumOAuthState
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

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
}

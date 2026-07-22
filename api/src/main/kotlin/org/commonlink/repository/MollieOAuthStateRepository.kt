package org.commonlink.repository

import org.commonlink.entity.MollieOAuthState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface MollieOAuthStateRepository : JpaRepository<MollieOAuthState, String> {

    /**
     * Returns true if the given association has at least one non-expired OAuth state record,
     * indicating that an authorization flow was initiated but not yet completed.
     */
    fun existsByAssociationIdAndExpiresAtAfter(associationId: UUID, threshold: Instant): Boolean

    /**
     * Deletes all OAuth state records for the given association.
     *
     * Called before creating a new auth URL to avoid stale states from abandoned flows.
     * Uses bulk JPQL to avoid the SELECT + N×DELETE pattern.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MollieOAuthState s WHERE s.association.id = :associationId")
    fun deleteByAssociationId(associationId: UUID)
}

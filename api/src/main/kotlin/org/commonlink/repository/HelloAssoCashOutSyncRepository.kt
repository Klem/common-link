package org.commonlink.repository

import org.commonlink.entity.HelloAssoCashOutSync
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/** Per-association outcome of the HelloAsso cash-out tracking, keyed by association id. */
interface HelloAssoCashOutSyncRepository : JpaRepository<HelloAssoCashOutSync, UUID> {

    /**
     * Inserts the association's row when missing; no-op otherwise (concurrent callers never fail on the key).
     * Native `ON CONFLICT DO NOTHING`.
     */
    @Modifying
    @Query(
        value = "INSERT INTO helloasso_cash_out_syncs (association_id) VALUES (:associationId) ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfMissing(@Param("associationId") associationId: UUID): Int

    /**
     * Claims a tracking pass for the association: sets [HelloAssoCashOutSync.lastRefreshRequestedAt] to [now] only
     * when the previous claim is older than [notBefore]. A single conditional UPDATE, so that it holds across
     * instances: of several simultaneous claims, one updates the row (returns 1) and the others none (return 0).
     */
    @Modifying
    @Query("""
        UPDATE HelloAssoCashOutSync s
        SET s.lastRefreshRequestedAt = :now, s.updatedAt = :now
        WHERE s.associationId = :associationId
          AND (s.lastRefreshRequestedAt IS NULL OR s.lastRefreshRequestedAt <= :notBefore)
    """)
    fun claimRefresh(
        @Param("associationId") associationId: UUID,
        @Param("now") now: Instant,
        @Param("notBefore") notBefore: Instant,
    ): Int
}

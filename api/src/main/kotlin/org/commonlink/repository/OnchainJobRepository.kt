package org.commonlink.repository

import org.commonlink.entity.OnchainJob
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OnchainJobRepository : JpaRepository<OnchainJob, UUID> {

    /**
     * Lock and return up to [batch] claimable `PENDING` jobs of chain [chainId], oldest first.
     *
     * A job is claimable when it has no dependency or when its dependency is `DONE`. Jobs of other
     * chains are never returned (a mock job is never dispatched to a real chain, and vice versa).
     * `FOR UPDATE SKIP LOCKED` guarantees that, even with multiple backend instances, no two workers
     * pick the same job — competing readers simply skip locked rows.
     */
    @Query(
        value = """
            SELECT j.* FROM onchain_jobs j
             WHERE j.status = 'PENDING'
               AND j.chain_id = :chainId
               AND (j.depends_on_job_id IS NULL
                    OR EXISTS (SELECT 1 FROM onchain_jobs d
                                WHERE d.id = j.depends_on_job_id AND d.status = 'DONE'))
             ORDER BY j.created_at
             LIMIT :batch
             FOR UPDATE OF j SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockBatch(@Param("chainId") chainId: Long, @Param("batch") batch: Int): List<OnchainJob>

    /**
     * Counts the `PENDING` jobs of chain [chainId] whose dependency is `FAILED`: they will never be claimed
     * until an operator deals with the failed prerequisite.
     */
    @Query(
        value = """
            SELECT COUNT(*) FROM onchain_jobs j
              JOIN onchain_jobs d ON d.id = j.depends_on_job_id
             WHERE j.status = 'PENDING' AND j.chain_id = :chainId AND d.status = 'FAILED'
        """,
        nativeQuery = true,
    )
    fun countBlockedByFailedDependency(@Param("chainId") chainId: Long): Long

    /** The job recording [correlationKey] on chain [chainId], if any (idempotency is scoped per chain). */
    fun findByChainIdAndCorrelationKey(chainId: Long, correlationKey: String): OnchainJob?
}

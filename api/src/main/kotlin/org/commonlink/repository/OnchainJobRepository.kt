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

    /**
     * The most recent job declaring a budget hash for a campaign on chain [chainId]: its `REGISTER_CAMPAIGN`
     * job (key [registrationKey]) or its latest `UPDATE_CAMPAIGN_BUDGET` job (key starting with [updateKeyPrefix]),
     * whatever their status. Its payload carries the budget hash the backend last declared for that campaign
     * on that chain. Portable JPQL (no JSONB operator): the test profile runs on H2.
     *
     * The `action` condition on the LIKE arm lets Postgres use the partial index `idx_onchain_jobs_budget_update_key`
     * (V89); the prefix must be LIKE-escaped with `!` (`_` is a wildcard) for the index range to cover it entirely.
     *
     * @param registrationKey `REGISTER_CAMPAIGN:<campaignId>`.
     * @param escapedUpdateKeyPrefix `UPDATE_CAMPAIGN_BUDGET:<campaignId>:`, LIKE-escaped with `!` (the key suffix
     *   is the previous budget job id).
     */
    @Query(
        """
            SELECT j FROM OnchainJob j
             WHERE j.chainId = :chainId
               AND (j.correlationKey = :registrationKey
                    OR (j.action = org.commonlink.entity.OnchainJobAction.UPDATE_CAMPAIGN_BUDGET
                        AND j.correlationKey LIKE CONCAT(:escapedUpdateKeyPrefix, '%') ESCAPE '!'))
             ORDER BY j.createdAt DESC, j.id DESC
             LIMIT 1
        """,
    )
    fun findLatestBudgetJob(
        @Param("chainId") chainId: Long,
        @Param("registrationKey") registrationKey: String,
        @Param("escapedUpdateKeyPrefix") escapedUpdateKeyPrefix: String,
    ): OnchainJob?

    /** The job recording [correlationKey] on chain [chainId], if any (idempotency is scoped per chain). */
    fun findByChainIdAndCorrelationKey(chainId: Long, correlationKey: String): OnchainJob?
}

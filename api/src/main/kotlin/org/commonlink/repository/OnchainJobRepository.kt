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
     * Lock and return up to [batch] `PENDING` jobs, oldest first.
     *
     * `FOR UPDATE SKIP LOCKED` guarantees that, even with multiple backend instances,
     * no two workers pick the same job — competing readers simply skip locked rows.
     */
    @Query(
        value = """
            SELECT * FROM onchain_jobs
             WHERE status = 'PENDING'
             ORDER BY created_at
             LIMIT :batch
             FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockBatch(@Param("batch") batch: Int): List<OnchainJob>

    fun findByCorrelationKey(correlationKey: String): OnchainJob?
}

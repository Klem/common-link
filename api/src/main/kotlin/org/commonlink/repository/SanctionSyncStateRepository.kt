package org.commonlink.repository

import org.commonlink.entity.SanctionSyncState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * Persistence for the single-row [SanctionSyncState] record.
 *
 * Only [org.commonlink.service.SanctionSyncExecutor] should call [acquireWriteLock] — it is
 * the distributed-lock gate that prevents concurrent ingestion across application instances.
 */
interface SanctionSyncStateRepository : JpaRepository<SanctionSyncState, Short> {

    /**
     * Blocks until an exclusive lock on the single row (id = 1) of `sanctions_sync_state` is
     * held, released automatically at transaction end. Serialises concurrent sync attempts so
     * only one instance ingests at a time.
     *
     * Same pattern as [org.commonlink.repository.ComplianceAuditLogRepository.acquireWriteLock]:
     * a portable `SELECT … FOR UPDATE` rather than `pg_advisory_xact_lock`.
     */
    @Query(value = "SELECT id FROM sanctions_sync_state WHERE id = 1 FOR UPDATE", nativeQuery = true)
    fun acquireWriteLock(): Number
}

package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * Single-row record tracking the lifecycle of scheduled sanctions-register synchronisations.
 *
 * The row (id = 1) is created by V58 migration and never deleted. It doubles as the
 * distributed-lock target for [org.commonlink.repository.SanctionSyncStateRepository.acquireWriteLock]:
 * `SELECT … FOR UPDATE` on this row serialises concurrent sync attempts across application
 * instances, preventing double-ingestion without advisory locks.
 *
 * - [lastAttemptAt] — set at the start of every attempt, committed whether the attempt
 *   succeeds or fails.
 * - [lastSuccessAt] — set only on successful ingestion; never regresses. Screening controls
 *   may read this value to assess register freshness and include it in their audit entries.
 * - [lastPublicationDate] — the DG Trésor publication date of the last successfully ingested
 *   snapshot. This is the "version de liste utilisée" referenced in the compliance audit journal.
 */
@Entity
@Table(name = "sanctions_sync_state")
class SanctionSyncState(
    @Id
    @Column(name = "id", updatable = false)
    val id: Short = 1,

    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null,

    @Column(name = "last_success_at")
    var lastSuccessAt: Instant? = null,

    @Column(name = "last_publication_date")
    var lastPublicationDate: LocalDate? = null,
)

package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Outcome of the HelloAsso cash-out tracking for one association: when it last ran ([lastSyncAt]) and why it failed
 * ([syncError]), read by the Payments tab; plus the guard of the on-demand refresh ([lastRefreshRequestedAt], claimed
 * by an atomic update so that it holds across instances) and the deduplication of the association-level alerts.
 *
 * Created by the first tracking pass of an association that has donations to track.
 */
@Entity
@Table(name = "helloasso_cash_out_syncs")
class HelloAssoCashOutSync(

    @Id
    @Column(name = "association_id", nullable = false, updatable = false)
    val associationId: UUID,

    /** End of the last pass that read HelloAsso for this association (successful or not). */
    @Column(name = "last_sync_at")
    var lastSyncAt: Instant? = null,

    /** Why the last pass failed (stable code, never a HelloAsso body); null when it succeeded. */
    @Column(name = "sync_error", length = 64)
    var syncError: String? = null,

    /** Passes failed in a row; reset by a successful one. */
    @Column(name = "consecutive_failures", nullable = false)
    var consecutiveFailures: Int = 0,

    /** When the repeated-failure alert was raised for the current failure streak. */
    @Column(name = "failure_alerted_at")
    var failureAlertedAt: Instant? = null,

    /** When the "donations not paid out while the HelloAsso link is gone" alert was raised; null once relinked. */
    @Column(name = "unlinked_alerted_at")
    var unlinkedAlertedAt: Instant? = null,

    /** Last pass started for this association (scheduled or on demand): the on-demand refresh guard. */
    @Column(name = "last_refresh_requested_at")
    var lastRefreshRequestedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

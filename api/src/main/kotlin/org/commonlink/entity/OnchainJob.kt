package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Outbox row representing a single on-chain action to dispatch to [org.commonlink.onchain.OnchainRegistryClient].
 *
 * The worker drains rows in `PENDING` status, locks them with `FOR UPDATE SKIP LOCKED`,
 * dispatches the action, and updates the row to `DONE` or back to `PENDING` (with retry)
 * or `FAILED` (terminal) depending on outcome.
 *
 * Idempotency is enforced via [correlationKey] — callers pass a stable key
 * (e.g. `"DONATION:<uuid>"`) and the service short-circuits if a row already exists.
 */
@Entity
@Table(name = "onchain_jobs")
class OnchainJob(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    val action: OnchainJobAction,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    val payloadJson: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OnchainJobStatus = OnchainJobStatus.PENDING,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "tx_hash", length = 66)
    var txHash: String? = null,

    @Column(name = "block_number")
    var blockNumber: Long? = null,

    @Column(name = "correlation_key", length = 128, unique = true)
    val correlationKey: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

enum class OnchainJobStatus { PENDING, RUNNING, DONE, FAILED }

enum class OnchainJobAction {
    VERIFY_ASSOCIATION, REVOKE_ASSOCIATION, RESTORE_ASSOCIATION,
    CREATE_CAMPAIGN, PUBLISH_CAMPAIGN, REVERT_CAMPAIGN_TO_DRAFT, UPDATE_CAMPAIGN_BUDGET,
    PAUSE_CAMPAIGN, UNPAUSE_CAMPAIGN, CANCEL_CAMPAIGN, COMPLETE_CAMPAIGN,
    RECORD_DONATION, MARK_MILESTONE_REACHED,
    RECORD_PAYOUT,
}

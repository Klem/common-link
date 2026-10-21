package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
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
 * Idempotency is enforced via [correlationKey] **per chain** ([chainId]) — callers pass a stable key
 * (e.g. `"DONATION:<uuid>"`) and the service short-circuits if a row already exists on the current
 * chain. The same fact can therefore be recorded once in mock (chain 0) and once on each real chain.
 *
 * Ordering: a job with a [dependsOnJobId] is only claimed once that prerequisite job is `DONE`
 * (campaign registration after association registration; donation / payout / budget after campaign
 * registration).
 */
@Entity
@Table(
    name = "onchain_jobs",
    uniqueConstraints = [UniqueConstraint(name = "uq_onchain_jobs_chain_correlation", columnNames = ["chain_id", "correlation_key"])],
)
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

    @Column(name = "correlation_key", length = 128)
    val correlationKey: String? = null,

    /** Chain the job targets; `0` is the mock sentinel (synthetic receipts, nothing on any chain). */
    @Column(name = "chain_id", nullable = false)
    val chainId: Long = 0,

    /** Prerequisite job that must be `DONE` before this one can be claimed; null when none. */
    @Column(name = "depends_on_job_id")
    val dependsOnJobId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

enum class OnchainJobStatus { PENDING, RUNNING, DONE, FAILED }

/**
 * On-chain actions of the outbox, one per registry v2 write: association and campaign registration (once,
 * no lifecycle), budget update, donation, payout. Campaign statuses, association lifecycle and milestones are
 * backend-only and never recorded on-chain. Values must match the `onchain_jobs_action_check` constraint.
 */
enum class OnchainJobAction {
    REGISTER_ASSOCIATION,
    REGISTER_CAMPAIGN,
    UPDATE_CAMPAIGN_BUDGET,
    RECORD_DONATION,
    RECORD_PAYOUT,
}

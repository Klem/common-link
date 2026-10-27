package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonRawValue
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Maximum length of a pause / resume rationale; mirrored by the compliance screen (`maxLength`). */
const val ONCHAIN_RATIONALE_MAX_LENGTH = 1000

/**
 * Body of `POST /api/compliance/onchain/pause`. Mirrored by the compliance screen: the rationale is mandatory
 * (non-blank), at most [ONCHAIN_RATIONALE_MAX_LENGTH] characters, and journaled verbatim.
 */
data class OnchainPauseRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = ONCHAIN_RATIONALE_MAX_LENGTH)
    @field:Schema(description = "Why the registry is frozen; stored in the compliance audit log", example = "Clé recorder suspectée compromise")
    val rationale: String,
)

/**
 * Body of `POST /api/compliance/onchain/unpause`. Same rules as [OnchainPauseRequest].
 */
data class OnchainUnpauseRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = ONCHAIN_RATIONALE_MAX_LENGTH)
    @field:Schema(description = "Why the registry is resumed; stored in the compliance audit log", example = "Clé recorder remplacée, incident clos")
    val rationale: String,
)

/**
 * Outcome of an emergency pause of the on-chain registry.
 *
 * @property paused always true on success.
 * @property alreadyPaused true when the registry was already paused: no transaction was sent (the request is still
 *   journaled).
 * @property txHash hash of the `pause()` transaction; null when [alreadyPaused].
 * @property blockNumber block of that transaction; null when [alreadyPaused].
 * @property explorerTxUrl public explorer link to the transaction; null when [alreadyPaused] or no explorer is set.
 */
data class OnchainPauseResultDto(
    val paused: Boolean,
    val alreadyPaused: Boolean,
    val txHash: String?,
    val blockNumber: Long?,
    val explorerTxUrl: String?,
)

/**
 * Outcome of a resume of the on-chain registry.
 *
 * @property paused always false on success.
 * @property alreadyActive true when the registry was not paused: no transaction was sent (the request is still
 *   journaled).
 * @property txHash hash of the `unpause()` transaction; null when [alreadyActive].
 * @property blockNumber block of that transaction; null when [alreadyActive].
 * @property explorerTxUrl public explorer link to the transaction; null when [alreadyActive] or no explorer is set.
 */
data class OnchainUnpauseResultDto(
    val paused: Boolean,
    val alreadyActive: Boolean,
    val txHash: String?,
    val blockNumber: Long?,
    val explorerTxUrl: String?,
)

/**
 * State of the on-chain registry as seen by the back-office.
 *
 * @property mock true in simulated mode: nothing is written on a chain and there is nothing to freeze.
 * @property chainId chain the backend currently targets (`0` in simulated mode).
 * @property registryAddress registry address; null in simulated mode.
 * @property paused whether the registry is paused; null when it could not be read (RPC unavailable).
 * @property canUnpause whether the platform key may still resume the registry (`UNPAUSER_ROLE`, revocable by the
 *   admin); false in simulated mode or when it could not be read.
 * @property explorerAddressUrl public explorer link to the registry; null in simulated mode or without explorer.
 */
data class OnchainRegistryStatusDto(
    val mock: Boolean,
    val chainId: Long,
    val registryAddress: String?,
    val paused: Boolean?,
    val canUnpause: Boolean,
    val explorerAddressUrl: String?,
)

/**
 * One freeze or resume request of the on-chain registry, from the compliance audit log.
 *
 * @property sequenceNo journal sequence number.
 * @property eventType `ONCHAIN_REGISTRY_PAUSED`, `…_PAUSE_FAILED`, `…_UNPAUSED` or `…_UNPAUSE_FAILED`.
 * @property actorUserId the compliance officer who asked.
 * @property actorEmail that officer's email; null when the account no longer exists.
 * @property payload raw journal payload (rationale, chain, registry, tx hash or error type, no-op flag).
 * @property occurredAt when it was journaled.
 */
data class OnchainControlEntryDto(
    val sequenceNo: Long,
    val eventType: String,
    val actorUserId: UUID?,
    val actorEmail: String?,
    @JsonRawValue val payload: String,
    val occurredAt: Instant,
)

/**
 * Health of the on-chain outbox for the chain the backend currently targets.
 *
 * @property chainId chain the counts refer to (`0` in simulated mode).
 * @property pending jobs waiting to be sent (held while the registry is paused).
 * @property running jobs claimed by the worker and not settled yet.
 * @property failed jobs that exhausted their attempts or were refused for good; they need an operator.
 * @property blockedByFailedDependency pending jobs that will never be claimed because their prerequisite failed.
 * @property oldestPendingSince creation date of the oldest pending job; null when none is pending.
 */
data class OnchainOutboxHealthDto(
    val chainId: Long,
    val pending: Long,
    val running: Long,
    val failed: Long,
    val blockedByFailedDependency: Long,
    val oldestPendingSince: Instant?,
)

/**
 * One registry write of the live feed (any status), read-only.
 *
 * @property id job id.
 * @property action registry write it carries.
 * @property status `PENDING`, `RUNNING`, `DONE` or `FAILED`.
 * @property attempts attempts consumed.
 * @property txHash transaction hash once sent; random in simulated mode (exists on no chain).
 * @property blockNumber block of that transaction.
 * @property explorerTxUrl public explorer link; null in simulated mode, before sending or without explorer.
 * @property error last error with every URL masked; null when none.
 * @property createdAt when the job was queued.
 * @property updatedAt when it last changed.
 */
data class OnchainJobFeedItemDto(
    val id: UUID,
    val action: String,
    val status: String,
    val attempts: Int,
    val txHash: String?,
    val blockNumber: Long?,
    val explorerTxUrl: String?,
    val error: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Live feed of the registry writes of the chain the backend currently targets.
 *
 * @property chainId chain of the feed (`0` in simulated mode).
 * @property simulated true in simulated mode: the writes and their hashes are simulated, nothing reaches a chain.
 * @property items the fifty most recently queued writes, newest first.
 */
data class OnchainJobFeedDto(
    val chainId: Long,
    val simulated: Boolean,
    val items: List<OnchainJobFeedItemDto>,
)

/**
 * A failed on-chain job, read-only (resetting it is an operator decision, never a compliance one).
 *
 * @property id job id.
 * @property action registry write it carried.
 * @property attempts attempts consumed.
 * @property error last error with every URL masked (an RPC URL may embed an API key); null when none was recorded.
 * @property createdAt when the job was queued.
 * @property updatedAt when it last changed (i.e. when it failed).
 */
data class OnchainFailedJobDto(
    val id: UUID,
    val action: String,
    val attempts: Int,
    val error: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

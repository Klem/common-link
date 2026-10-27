package org.commonlink.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/**
 * Body of `POST /api/compliance/onchain/pause`. A future back-office screen must mirror this validation: the
 * rationale is mandatory (non-blank) and is journaled verbatim.
 */
data class OnchainPauseRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Schema(description = "Why the registry is frozen; stored in the compliance audit log", example = "Clé recorder suspectée compromise")
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
 * State of the on-chain registry as seen by the back-office.
 *
 * @property mock true in simulated mode: nothing is written on a chain and there is nothing to freeze.
 * @property chainId chain the backend currently targets (`0` in simulated mode).
 * @property registryAddress registry address; null in simulated mode.
 * @property paused whether the registry is paused; null when it could not be read (RPC unavailable).
 * @property explorerAddressUrl public explorer link to the registry; null in simulated mode or without explorer.
 */
data class OnchainRegistryStatusDto(
    val mock: Boolean,
    val chainId: Long,
    val registryAddress: String?,
    val paused: Boolean?,
    val explorerAddressUrl: String?,
)

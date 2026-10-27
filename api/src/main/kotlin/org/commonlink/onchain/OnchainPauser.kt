package org.commonlink.onchain

import org.web3j.protocol.core.methods.response.TransactionReceipt

/**
 * Emergency freeze and resume of the on-chain registry (`pause()` with `PAUSER_ROLE`, `unpause()` with `UNPAUSER_ROLE`,
 * decision 31 revised on 2026-10-27).
 *
 * Kept apart from [OnchainRegistry] on purpose: both are signed by a dedicated key, never by the recorder, and the
 * recorder's records are never signed by this key. The registry admin can revoke `UNPAUSER_ROLE` from this key at any
 * time (once the Safe exists): [canUnpause] then turns false and resuming is the admin's alone.
 *
 * Only [OnchainPauserClient] implements it, in real mode; in mock mode no bean exists (there is no registry to freeze).
 */
interface OnchainPauser {

    /**
     * Sends `pause()` and waits for its receipt. Reverts (`EnforcedPause`) when the registry is already paused: callers
     * read [OnchainRegistry.isPaused] first.
     *
     * @return the mined receipt.
     */
    fun pause(): TransactionReceipt

    /**
     * Sends `unpause()` and waits for its receipt. Reverts (`ExpectedPause`) when the registry is not paused, and
     * (`AccessControlUnauthorizedAccount`) when the key lost `UNPAUSER_ROLE`: callers read [OnchainRegistry.isPaused]
     * and [canUnpause] first.
     *
     * @return the mined receipt.
     */
    fun unpause(): TransactionReceipt

    /**
     * Reads whether the pauser key still holds `UNPAUSER_ROLE` (the admin may revoke it without redeploying).
     *
     * @return true when this key may resume the registry.
     */
    fun canUnpause(): Boolean
}

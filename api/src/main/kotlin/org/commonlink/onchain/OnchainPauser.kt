package org.commonlink.onchain

import org.web3j.protocol.core.methods.response.TransactionReceipt

/**
 * Emergency freeze of the on-chain registry (`pause()`, `PAUSER_ROLE`, decision 31).
 *
 * Kept apart from [OnchainRegistry] on purpose: the pause is signed by a dedicated key, never by the recorder, and
 * the recorder's records are never signed by this key. There is no `unpause` here: resuming is reserved to the
 * registry admin (a Safe multisig in production), which the backend never holds.
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
}

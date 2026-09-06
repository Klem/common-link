package org.commonlink.dto

/**
 * Payload for `PATCH /api/association/payees/:id/ibans/:ibanId`.
 * Currently only exposes the [active] toggle.
 */
data class PatchIbanRequest(
    /** New active state for the IBAN. */
    val active: Boolean
)

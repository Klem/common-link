package org.commonlink.dto

/**
 * Payload for `PATCH /api/association/payees/:id`.
 * Currently only exposes the [active] toggle.
 */
data class PatchPayeeRequest(
    /** New active state for the payee. */
    val active: Boolean
)

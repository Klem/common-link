package org.commonlink.dto.monerium

import jakarta.validation.constraints.NotBlank

/**
 * IBAN provisioned by Monerium for a linked wallet.
 *
 * `state` reflects the IBAN lifecycle (e.g. requested / assigned / active). `bic` is
 * optional because some Monerium environments omit it from the initial response.
 */
data class MoneriumIbanDto(
    val iban: String,
    val bic: String?,
    val profile: String?,
    val address: String,
    val chain: String,
    val state: String?,
)

/** Request body sent to Monerium `POST /ibans`. The address must already be linked (see §E). */
data class RequestIbanRequest(
    @field:NotBlank val address: String,
    @field:NotBlank val chain: String,
)

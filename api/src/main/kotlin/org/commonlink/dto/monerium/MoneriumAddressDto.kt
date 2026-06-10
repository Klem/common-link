package org.commonlink.dto.monerium

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

/**
 * Wallet address as returned by Monerium `/addresses`.
 *
 * Monerium may report either a single-chain shape (`chain`) or multi-chain shape (`chains`);
 * we keep both nullable so the deserializer is tolerant.
 */
data class MoneriumAddressDto(
    val id: String?,
    val profile: String?,
    val address: String,
    val chain: String?,
    val chains: List<String>? = null,
)

/** Envelope for `GET /addresses` response. */
data class MoneriumAddressListDto(
    @field:JsonProperty("addresses") val addresses: List<MoneriumAddressDto> = emptyList(),
)

/**
 * Request body sent to Monerium `POST /addresses` to link a wallet.
 *
 * The signature is a personal_sign of the fixed Monerium ownership message produced
 * by the wallet's private key on the frontend; the backend never holds it.
 */
data class LinkAddressRequest(
    @field:JsonProperty("profile")
    @field:NotBlank val profileId: String,
    @field:NotBlank val address: String,
    @field:NotBlank val chain: String,
    @field:NotBlank val message: String,
    @field:NotBlank val signature: String,
)

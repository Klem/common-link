package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for adding an IBAN to a beneficiary.
 *
 * The backend performs format validation (mod-97 checksum) regardless of any frontend validation —
 * every click can be replayed as a direct API call.
 *
 * @param iban The IBAN string to register. Must not be blank and must not exceed 34 characters
 *             (the ISO 13616 maximum length). Leading/trailing spaces are stripped by the service.
 */
data class AddIbanRequest(
    @field:NotBlank
    @field:Size(max = 34, message = "IBAN must not exceed 34 characters")
    val iban: String
)

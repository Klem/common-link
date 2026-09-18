package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AssociationProfileUpsertDto(
    @field:NotBlank
    val nom: String,

    /**
     * RNA (`W` + 9 alphanumerics) or 9-digit SIREN. Mirrors the sign-up screen, which refuses anything
     * else: the JOAFE dataset exposes legacy announcement numbers in its RNA column, and such a value
     * identifies no legal entity yet would be searched as a SIREN by the registry check.
     */
    @field:NotBlank
    @field:Size(max = 20)
    @field:Pattern(
        regexp = "^(W[0-9A-Za-z]{9}|[0-9]{9})$",
        message = "identifier must be a RNA (W followed by 9 alphanumerics) or a 9-digit SIREN",
    )
    val identifier: String,

    val ville: String? = null,
    val codePostal: String? = null,
    val contact: String? = null,
    val description: String? = null
)

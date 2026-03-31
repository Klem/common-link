package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AssociationProfileUpsertDto(
    @field:NotBlank
    val nom: String,

    @field:NotBlank
    @field:Size(min = 9, max = 9)
    val siren: String,

    val ville: String? = null,
    val codePostal: String? = null,
    val contact: String? = null,
    val description: String? = null
)

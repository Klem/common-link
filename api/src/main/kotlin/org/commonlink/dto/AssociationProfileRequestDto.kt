package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AssociationProfileRequestDto(
    @field:NotBlank
    val name: String,

    @field:NotBlank
    @field:Size(min = 9, max = 9)
    val identifier: String,

    val city: String? = null,
    val postalCode: String? = null,
    val contactName: String? = null,
    val description: String? = null
)

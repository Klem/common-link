package org.commonlink.dto

import org.commonlink.entity.UserRole
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class MagicLinkRequestDto(
    @field:Email
    @field:NotBlank
    val email: String,

    val role: UserRole? = null,

    @field:Valid
    val associationProfile: AssociationProfileRequestDto? = null
)

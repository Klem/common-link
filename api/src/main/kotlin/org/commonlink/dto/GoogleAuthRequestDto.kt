package org.commonlink.dto

import org.commonlink.entity.UserRole
import jakarta.validation.constraints.NotBlank

data class GoogleAuthRequestDto(
    @field:NotBlank
    val idToken: String,

    val role: UserRole? = null
)

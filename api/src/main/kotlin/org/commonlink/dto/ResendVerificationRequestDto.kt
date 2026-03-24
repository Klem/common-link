package org.commonlink.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class ResendVerificationRequestDto(
    @field:Email
    @field:NotBlank
    val email: String
)

package org.commonlink.dto

import jakarta.validation.constraints.NotBlank

data class VerifyEmailRequestDto(
    @field:NotBlank
    val token: String
)

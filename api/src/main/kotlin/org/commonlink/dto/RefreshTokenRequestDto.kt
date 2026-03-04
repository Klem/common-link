package org.commonlink.dto

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequestDto(
    @field:NotBlank
    val refreshToken: String
)

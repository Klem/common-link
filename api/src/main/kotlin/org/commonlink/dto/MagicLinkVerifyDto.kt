package org.commonlink.dto

import jakarta.validation.constraints.NotBlank

data class MagicLinkVerifyDto(
    @field:NotBlank
    val token: String
)

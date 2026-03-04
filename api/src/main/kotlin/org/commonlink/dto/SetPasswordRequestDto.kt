package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SetPasswordRequestDto(
    @field:NotBlank
    @field:Size(min = 8)
    val password: String,

    @field:NotBlank
    val confirmPassword: String
)

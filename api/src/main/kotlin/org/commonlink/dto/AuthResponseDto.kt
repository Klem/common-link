package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonInclude

data class AuthResponseDto(
    val accessToken: String,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val refreshToken: String?,
    val user: UserDto
)

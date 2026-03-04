package org.commonlink.dto

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto
)

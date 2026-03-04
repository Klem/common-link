package org.commonlink.dto

import org.commonlink.entity.AuthProvider
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import java.time.Instant
import java.util.UUID

data class UserDto(
    val id: UUID,
    val email: String,
    val role: UserRole,
    val provider: AuthProvider,
    val displayName: String?,
    val avatarUrl: String?,
    val emailVerified: Boolean,
    val createdAt: Instant
)

fun User.toDto() = UserDto(
    id = id,
    email = email,
    role = role,
    provider = provider,
    displayName = displayName,
    avatarUrl = avatarUrl,
    emailVerified = emailVerified,
    createdAt = createdAt
)

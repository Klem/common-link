package org.commonlink.dto

import org.commonlink.entity.DonorProfile
import java.util.UUID

data class DonorProfileDto(
    val id: UUID,
    val displayName: String?,
    val anonymous: Boolean
)

fun DonorProfile.toDto() = DonorProfileDto(
    id = id!!,
    displayName = displayName,
    anonymous = anonymous
)

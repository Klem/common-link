package org.commonlink.dto

data class UpdateDonorProfileRequest(
    val displayName: String?,
    val anonymous: Boolean?
)

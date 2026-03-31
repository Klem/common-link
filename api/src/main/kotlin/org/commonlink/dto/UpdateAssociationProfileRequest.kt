package org.commonlink.dto

data class UpdateAssociationProfileRequest(
    val contactName: String?,
    val city: String?,
    val postalCode: String?,
    val description: String?
)

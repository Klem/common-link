package org.commonlink.dto

import org.commonlink.entity.AssociationProfile
import java.util.UUID

data class AssociationProfileDto(
    val id: UUID,
    val name: String,
    val identifier: String,
    val city: String?,
    val postalCode: String?,
    val contactName: String?,
    val description: String?,
    val verified: Boolean
)

fun AssociationProfile.toDto() = AssociationProfileDto(
    id = id!!,
    name = name,
    identifier = identifier,
    city = city,
    postalCode = postalCode,
    contactName = contactName,
    description = description,
    verified = verified
)

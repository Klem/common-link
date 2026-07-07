package org.commonlink.dto

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.VerificationStatus
import java.time.Instant
import java.util.UUID

data class AssociationProfileDto(
    val id: UUID,
    val name: String,
    val identifier: String,
    val city: String?,
    val postalCode: String?,
    val contactName: String?,
    val description: String?,
    val rna: String?,
    val creationYear: Short?,
    val contactEmail: String?,
    val phone: String?,
    val verificationStatus: VerificationStatus,
    val verificationRejectionReason: String?,
    val verificationSubmittedAt: Instant?,
    val verifiedAt: Instant?,
)

fun AssociationProfile.toDto() = AssociationProfileDto(
    id = id!!,
    name = name,
    identifier = identifier,
    city = city,
    postalCode = postalCode,
    contactName = contactName,
    description = description,
    rna = rna,
    creationYear = creationYear,
    contactEmail = contactEmail,
    phone = phone,
    verificationStatus = verificationStatus,
    verificationRejectionReason = verificationRejectionReason,
    verificationSubmittedAt = verificationSubmittedAt,
    verifiedAt = verifiedAt,
)

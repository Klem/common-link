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
    val siren: String?,
    val creationYear: Short?,
    val contactEmail: String?,
    val phone: String?,
    val verificationStatus: VerificationStatus,
    val verificationRejectionReason: String?,
    val verificationSubmittedAt: Instant?,
    val verifiedAt: Instant?,
    /** Public opaque widget token (`clk_…`). Null if the widget is inactive. */
    val widgetToken: String?,
    /** UUID of the campaign configured as donation destination for the widget. */
    val widgetDestinationCampaignId: UUID?,
)

fun AssociationProfile.toDto() = AssociationProfileDto(
    id = id!!,
    name = name,
    identifier = identifier,
    city = city,
    postalCode = postalCode,
    contactName = contactName,
    description = description,
    siren = siren,
    creationYear = creationYear,
    contactEmail = contactEmail,
    phone = phone,
    verificationStatus = verificationStatus,
    verificationRejectionReason = verificationRejectionReason,
    verificationSubmittedAt = verificationSubmittedAt,
    verifiedAt = verifiedAt,
    widgetToken = widgetToken,
    widgetDestinationCampaignId = widgetDestinationCampaign?.id,
)

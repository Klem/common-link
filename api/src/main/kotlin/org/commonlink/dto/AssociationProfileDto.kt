package org.commonlink.dto

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.LandingTheme
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
    /** Allowed origin for widget post-payment redirects. Null if not configured. */
    val widgetAllowedOrigin: String?,
    /** Full street address of the registered office. Null if not yet filled. */
    val addressLine1: String?,
    /** Official purpose / objet social. Null if not yet filled. */
    val legalObject: String?,
    /** Full name of the authorised receipt signer. Null if not yet filled. */
    val signerName: String?,
    /** Role/title of the authorised signer. Null if not yet filled. */
    val signerRole: String?,
    /** Visual palette of the donation landing page. */
    val landingTheme: LandingTheme,
    /** Public serving path of the landing logo. Null if no logo has been uploaded. */
    val landingLogo: String?,
    /** Whether the landing page shows the "what this donation funds" section. */
    val landingShowProject: Boolean,
    /** Whether the landing page shows the transparency section. */
    val landingShowTransparency: Boolean,
    /** Whether the landing page shows the "donate with confidence" section. */
    val landingShowTrust: Boolean,
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
    widgetAllowedOrigin = widgetAllowedOrigin,
    addressLine1 = addressLine1,
    legalObject = legalObject,
    signerName = signerName,
    signerRole = signerRole,
    landingTheme = landingTheme,
    landingLogo = landingLogo,
    landingShowProject = landingShowProject,
    landingShowTransparency = landingShowTransparency,
    landingShowTrust = landingShowTrust,
)

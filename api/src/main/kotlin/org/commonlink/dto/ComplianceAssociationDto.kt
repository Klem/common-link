package org.commonlink.dto

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.RiskLevel
import org.commonlink.entity.VerificationStatus
import java.time.Instant
import java.util.UUID

/**
 * One row of the compliance « Associations » index — enough to identify a dossier and its
 * current standing without loading the full detail.
 */
data class ComplianceAssociationSummaryDto(
    val id: UUID,
    val name: String,
    val identifier: String,
    val status: AssociationStatus,
    val verificationStatus: VerificationStatus,
    val riskLevel: RiskLevel,
)

fun AssociationProfile.toComplianceSummaryDto() = ComplianceAssociationSummaryDto(
    id = id!!,
    name = name,
    identifier = identifier,
    status = status,
    verificationStatus = verificationStatus,
    riskLevel = riskLevel,
)

/**
 * Full compliance dossier of an association — status, KYB standing, and every legal-identity
 * field held on [AssociationProfile]. Deliberately its own DTO rather than a reuse of the
 * association-dashboard's own profile DTO: this is a distinct read boundary (COMPLIANCE_OFFICER
 * browsing any association, not an association reading its own record), and coupling the two
 * would make a future change to one silently reshape the other.
 */
data class ComplianceAssociationDetailDto(
    val id: UUID,
    val name: String,
    val identifier: String,
    val siren: String?,
    val addressLine1: String?,
    val legalObject: String?,
    val signerName: String?,
    val signerRole: String?,
    val city: String?,
    val postalCode: String?,
    val creationYear: Short?,
    val contactName: String?,
    val contactEmail: String?,
    val phone: String?,
    val status: AssociationStatus,
    val verificationStatus: VerificationStatus,
    val verifiedAt: Instant?,
    val riskLevel: RiskLevel,
    val riskLevelAssessedAt: Instant?,
    val riskClassificationVersion: String?,
)

fun AssociationProfile.toComplianceDetailDto() = ComplianceAssociationDetailDto(
    id = id!!,
    name = name,
    identifier = identifier,
    siren = siren,
    addressLine1 = addressLine1,
    legalObject = legalObject,
    signerName = signerName,
    signerRole = signerRole,
    city = city,
    postalCode = postalCode,
    creationYear = creationYear,
    contactName = contactName,
    contactEmail = contactEmail,
    phone = phone,
    status = status,
    verificationStatus = verificationStatus,
    verifiedAt = verifiedAt,
    riskLevel = riskLevel,
    riskLevelAssessedAt = riskLevelAssessedAt,
    riskClassificationVersion = riskClassificationVersion,
)

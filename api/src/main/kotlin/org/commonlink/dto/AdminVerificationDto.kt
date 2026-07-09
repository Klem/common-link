package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.commonlink.entity.VerificationStatus
import java.time.Instant
import java.util.UUID

/**
 * Summary of one association's KYC dossier for the admin list view.
 *
 * @param associationId The association profile UUID.
 * @param docCount Number of required verification documents already uploaded (max 3).
 */
data class AdminVerificationSummaryDto(
    val associationId: UUID,
    val name: String,
    val identifier: String,
    val status: VerificationStatus,
    val submittedAt: Instant?,
    val docCount: Int,
)

/**
 * Full detail of one association's KYC dossier for the admin review view.
 *
 * @param requiredDocuments Metadata for each of the 3 required slots (never contains content bytes).
 * @param optionalDocuments All supplementary documents uploaded by the association.
 */
data class AdminVerificationDetailDto(
    val associationId: UUID,
    val name: String,
    val identifier: String,
    val status: VerificationStatus,
    val rejectionReason: String?,
    val submittedAt: Instant?,
    val verifiedAt: Instant?,
    val docCount: Int,
    val requiredDocuments: List<DocumentSlotDto>,
    val optionalDocuments: List<OptionalDocumentDto>,
)

/**
 * Request body for rejecting a verification dossier.
 *
 * @param reason Admin-provided rejection reason shown to the association.
 */
data class RejectVerificationRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val reason: String,
)

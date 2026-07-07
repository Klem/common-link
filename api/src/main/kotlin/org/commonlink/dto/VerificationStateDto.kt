package org.commonlink.dto

import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.VerificationStatus
import java.time.Instant
import java.util.UUID

/**
 * State of an association's KYC verification dossier, including per-slot document metadata.
 *
 * @param status Current KYC lifecycle state.
 * @param rejectionReason Admin-provided reason, set when status is [VerificationStatus.REJECTED].
 * @param submittedAt Timestamp of the last submission (set when PENDING).
 * @param verifiedAt Timestamp of admin approval (set when VERIFIED).
 * @param requiredDocuments Metadata for each of the 3 required document slots.
 */
data class VerificationStateDto(
    val status: VerificationStatus,
    val rejectionReason: String?,
    val submittedAt: Instant?,
    val verifiedAt: Instant?,
    val requiredDocuments: List<DocumentSlotDto>,
)

/**
 * Metadata for one required verification document slot.
 *
 * @param docType The document type this slot represents.
 * @param uploaded Whether a document has been uploaded for this slot.
 * @param id Document id, present when uploaded.
 * @param fileName Original file name, present when uploaded.
 * @param sizeBytes File size in bytes, present when uploaded.
 * @param uploadedAt Upload timestamp, present when uploaded.
 */
data class DocumentSlotDto(
    val docType: AssociationDocumentType,
    val uploaded: Boolean,
    val id: UUID?,
    val fileName: String?,
    val sizeBytes: Long?,
    val uploadedAt: Instant?,
)

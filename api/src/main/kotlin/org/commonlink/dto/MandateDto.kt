package org.commonlink.dto

import jakarta.validation.constraints.NotNull
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.MandateEligibility
import java.time.Instant
import java.util.UUID

/**
 * Metadata for one required mandate document slot (statuts or rescrit fiscal).
 *
 * @param docType The mandate document type this slot represents.
 * @param uploaded Whether a document has been uploaded for this slot.
 * @param id Document id, present when uploaded.
 * @param fileName Original file name, present when uploaded.
 * @param sizeBytes File size in bytes, present when uploaded.
 * @param uploadedAt Upload timestamp, present when uploaded.
 */
data class MandateDocumentSlotDto(
    val docType: AssociationDocumentType,
    val uploaded: Boolean,
    val id: UUID?,
    val fileName: String?,
    val sizeBytes: Long?,
    val uploadedAt: Instant?,
)

/**
 * Full state of an association's fiscal mandate.
 *
 * @param signed Whether an active (non-revoked) mandate currently exists.
 * @param reference Human-readable mandate reference (e.g. MND-2026-0001), present when signed.
 * @param signedAt Timestamp of the electronic signature, present when signed.
 * @param eligibility Declared tax-reduction eligibility category, present when signed.
 * @param revokedAt Timestamp of revocation if the last mandate was revoked; null on active mandates.
 * @param mandateDocs Metadata for the 2 required mandate document slots.
 * @param blocked True when the association is not yet VERIFIED — signing is gated on verification.
 */
data class MandateStateDto(
    val signed: Boolean,
    val reference: String?,
    val signedAt: Instant?,
    val eligibility: MandateEligibility?,
    val revokedAt: Instant?,
    val mandateDocs: List<MandateDocumentSlotDto>,
    val blocked: Boolean,
)

/**
 * Request body for signing a fiscal mandate.
 *
 * @param eligibility Declared eligibility category for tax-receipt issuance. Required.
 * @param accepted Must be true — the association confirms acceptance of the mandate terms.
 */
data class SignMandateRequest(
    @field:NotNull val eligibility: MandateEligibility?,
    val accepted: Boolean,
)

package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.commonlink.entity.LegalAcceptanceSubjectType
import org.commonlink.entity.LegalContentFormat
import org.commonlink.entity.LegalDocumentType
import java.time.Instant
import java.util.UUID

/**
 * Current published text of a CGU/CGV document.
 *
 * @property version opaque identifier the client must send back when accepting this text.
 * @property format how to read [content]: plain text, or a serialized [LegalTextV1].
 */
data class LegalDocumentDto(
    val documentType: LegalDocumentType,
    val version: String,
    val format: LegalContentFormat,
    val content: String,
    val publishedAt: Instant,
)

/**
 * Body of the association's acceptance outside a publication (post-login gate).
 *
 * @property version the version the gate displayed, refused with 409 if no longer current.
 */
data class AcceptLegalDocumentRequest(
    @field:NotBlank
    @field:Size(max = 32)
    val version: String,
)

/**
 * One document's checkbox as sent by the campaign publish dialog.
 *
 * @property accepted whether the box was ticked.
 * @property version the version the dialog displayed, required when [accepted].
 */
data class PublishTermsAcceptance(
    val accepted: Boolean,
    val version: String?,
)

/**
 * Whether an association already has a standing acceptance of the current version of a document.
 * Drives the pre-checked/disabled state of the publish-time checkbox.
 */
data class LegalAcceptanceStateDto(
    val documentType: LegalDocumentType,
    val currentVersion: String,
    val accepted: Boolean,
)

/** One proof-of-acceptance row — backs the compliance restitution endpoint. */
data class LegalAcceptanceDto(
    val id: UUID,
    val subjectType: LegalAcceptanceSubjectType,
    val subjectId: UUID,
    val documentType: LegalDocumentType,
    val documentVersion: String,
    val acceptedAt: Instant,
    val signerName: String?,
    val signerEmail: String?,
    val donationId: UUID?,
    val campaignId: UUID?,
)

/**
 * One donor's CGU/CGV acceptance rows for a single campaign, grouped — a donor who gave twice to
 * the same campaign gets one group with every document from both donations, not two rows. Groups
 * are ordered by each donor's most recent acceptance, most recent first; [donorName]/[donorEmail]
 * are the signer snapshot from that same most-recent row (see [org.commonlink.entity.LegalAcceptance]
 * KDoc — never a live re-read of the donor's profile).
 */
data class DonorLegalAcceptanceGroupDto(
    val donorId: UUID,
    val donorName: String?,
    val donorEmail: String?,
    val acceptances: List<LegalAcceptanceDto>,
)

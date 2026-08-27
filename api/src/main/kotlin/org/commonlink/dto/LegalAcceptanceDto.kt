package org.commonlink.dto

import org.commonlink.entity.LegalAcceptanceSubjectType
import org.commonlink.entity.LegalDocumentType
import java.time.Instant
import java.util.UUID

/** Current published text of a CGU/CGV document. */
data class LegalDocumentDto(
    val documentType: LegalDocumentType,
    val version: String,
    val content: String,
    val publishedAt: Instant,
)

/**
 * Whether an association already has a standing acceptance of the current CGU version.
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

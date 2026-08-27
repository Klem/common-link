package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Proof that a donor or an association expressly accepted a versioned CGU/CGV text (notice ACPR ;
 * art. 1740 A CGI — without this record the association's CGU warranty clause is inopposable).
 *
 * [signerName]/[signerEmail] are snapshots taken **at acceptance time**, never re-read from the
 * live profile: `AssociationProfile.signerName` is a mutable `var` and could otherwise silently
 * rewrite the historical record of who accepted what.
 *
 * ### Two different shapes, one table
 * - `DONOR`: one row per document per donation — a donation is a transactional act, not a
 *   standing agreement, so acceptance is captured fresh every time. No uniqueness constraint;
 *   [donationId] identifies which donation it proves.
 * - `ASSOCIATION`: at most one row per (association, documentType, documentVersion) — enforced by
 *   the partial unique index `legal_acceptance_association_version_uq` (V73). Reused across every
 *   campaign published while the current CGU version is unchanged; [campaignId] records only the
 *   campaign whose publication triggered the very first acceptance of that version.
 *
 * [campaignId] is also set on `DONOR` rows, to the campaign the donation was made on.
 */
@Entity
@Table(name = "legal_acceptance")
class LegalAcceptance(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false, length = 16)
    val subjectType: LegalAcceptanceSubjectType,

    @Column(name = "subject_id", nullable = false, updatable = false)
    val subjectId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 10)
    val documentType: LegalDocumentType,

    @Column(name = "document_version", nullable = false, updatable = false, length = 32)
    val documentVersion: String,

    @Column(name = "accepted_at", nullable = false, updatable = false)
    val acceptedAt: Instant = Instant.now(),

    /** Snapshot of the signatory's name at acceptance time — never re-read from the live profile. */
    @Column(name = "signer_name", updatable = false, length = 255)
    val signerName: String?,

    /** Snapshot of the signatory's e-mail at acceptance time. */
    @Column(name = "signer_email", updatable = false, length = 255)
    val signerEmail: String?,

    /** Set when [subjectType] is DONOR — the donation this acceptance proves. */
    @Column(name = "donation_id", updatable = false)
    val donationId: UUID? = null,

    /**
     * ASSOCIATION: the campaign whose publication triggered this row.
     * DONOR: the campaign the donation was made on.
     */
    @Column(name = "campaign_id", updatable = false)
    val campaignId: UUID? = null,
)

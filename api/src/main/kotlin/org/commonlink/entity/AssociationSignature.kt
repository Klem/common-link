package org.commonlink.entity

import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Signature printed in the "Signature" box of the association's fiscal receipts (Cerfa 11580).
 *
 * An image signature, not an electronic signature in the eIDAS sense: it reproduces the authorised
 * signer's handwriting on the PDF, nothing more. It can only be written together with the signatory
 * attestation ([org.commonlink.service.SignatoryService.saveSignatory]), which is journaled in the
 * compliance audit log.
 *
 * Stored in its own table keyed by the association id (shared primary key) rather than on
 * [AssociationProfile]: fetching a profile must never drag the image bytes along (same reasoning as
 * [AssociationLogo]). Receipts already issued are unaffected by later changes: their PDF bytes are
 * stored once in [DonationReceipt].
 */
@Entity
@Table(name = "association_signature")
class AssociationSignature(

    /** Primary key — also the id of the association profile the signature belongs to. */
    @Id
    @Column(name = "association_id", nullable = false, updatable = false)
    val associationId: UUID,

    /** How the signature was produced; CHECK constraint `association_signature_mode_check` (V98). */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    var mode: SignatureMode,

    /** PNG or JPEG bytes, whatever the [mode] (NOT NULL, V98). */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "image", nullable = false, columnDefinition = "BYTEA")
    var image: ByteArray,

    /** MIME type sniffed from [image] (`image/png` or `image/jpeg`). */
    @Column(name = "content_type", nullable = false, length = 50)
    var contentType: String,

    /** Instant of the latest signatory attestation — every save re-attests. */
    @Column(name = "attested_at", nullable = false)
    var attestedAt: Instant,

    /** Instant of the latest save. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)

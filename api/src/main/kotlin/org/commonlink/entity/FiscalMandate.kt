package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * A signed fiscal mandate authorising CommonLink to issue tax receipts on behalf of the association.
 *
 * Multiple rows per association are allowed to preserve history (each re-signature after revocation
 * creates a new row). At most one active mandate is enforced by a partial unique index on
 * `(association_id) WHERE revoked_at IS NULL` — see V35 migration.
 *
 * The [reference] follows the pattern `MND-<year>-<seq %04d>` generated via `fiscal_mandate_ref_seq`.
 * Signing is gated on [AssociationProfile.verificationStatus] == VERIFIED at the service layer.
 */
@Entity
@Table(name = "fiscal_mandate")
class FiscalMandate(

    /** Auto-generated UUID primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The association that signed this mandate. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    val association: AssociationProfile,

    /** Declared eligibility category for tax-receipt issuance. */
    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility", nullable = false, length = 30)
    val eligibility: MandateEligibility,

    /** Unique human-readable reference, e.g. MND-2026-0001. */
    @Column(name = "reference", nullable = false, length = 20, unique = true)
    val reference: String,

    /** Timestamp of the electronic signature (server-side, set at signing). */
    @Column(name = "signed_at", nullable = false)
    val signedAt: Instant,

    /** Timestamp of revocation. Null while the mandate is active. */
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
)

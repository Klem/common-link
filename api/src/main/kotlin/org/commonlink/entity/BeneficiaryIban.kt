package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Stores a single IBAN entry for a [Beneficiary] along with its VOP verification result.
 *
 * An IBAN starts in [IbanVerificationStatus.PENDING] and transitions through verification
 * states as VOP (Verification of Payee) checks are performed. The [vopRawResponse] preserves
 * the full bank response for audit and compliance purposes.
 *
 * The pair (beneficiary_id, iban) is unique — an association cannot register the same IBAN
 * twice for the same beneficiary.
 */
@Entity
@Table(name = "beneficiary_ibans")
class BeneficiaryIban(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The beneficiary that owns this IBAN.*/
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    val beneficiary: Beneficiary,

    /** The IBAN string in standard format (up to 34 characters). */
    @Column(name = "iban", nullable = false, length = 34)
    val iban: String,

    /**
     * Current verification status of this IBAN.
     *
     * Mutable because it is updated after VOP checks or format validation steps.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: IbanVerificationStatus = IbanVerificationStatus.PENDING,

    /**
     * Outcome returned by the VOP service.
     *
     * Null until a VOP check has been attempted. Mutable because it may be updated on retry.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "vop_result", length = 30)
    var vopResult: VopResult? = null,

    /**
     * The account holder name suggested by the bank during VOP, if available.
     *
     * Useful for close-match cases where the association needs to review the discrepancy.
     */
    @Column(name = "vop_suggested_name", length = 255)
    var vopSuggestedName: String? = null,

    /** Full raw response from the VOP service, stored as JSON or plain text for audit purposes. */
    @Column(name = "vop_raw_response", columnDefinition = "TEXT")
    var vopRawResponse: String? = null,

    /** Timestamp when the VOP check was last completed. Null if no check has been run yet. */
    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    /** Timestamp of record creation; immutable after insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

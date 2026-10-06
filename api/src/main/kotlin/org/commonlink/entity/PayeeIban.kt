package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Stores a single IBAN entry for a [Payee] along with its VOP verification result.
 *
 * An IBAN starts in [IbanVerificationStatus.PENDING] and transitions through verification
 * states as VOP (Verification of Payee) checks are performed. The [vopRawResponse] preserves
 * the full bank response for audit and compliance purposes.
 *
 * The pair (payee_id, iban_fingerprint) is unique — an association cannot register the same IBAN
 * twice for the same payee. [iban] itself is encrypted at rest (AES-256-GCM, random IV per write
 * — see [ComplianceCryptoConverter]), so it can't back that constraint; [ibanFingerprint] (a
 * deterministic HMAC, see [IbanFingerprint]) does instead (security audit 2026-10-06, finding #2).
 */
@Entity
@Table(name = "payee_ibans")
class PayeeIban(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The payee that owns this IBAN.*/
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_id", nullable = false)
    val payee: Payee,

    /** The IBAN string in standard format, encrypted at rest via [ComplianceCryptoConverter]. */
    @Convert(converter = ComplianceCryptoConverter::class)
    @Column(name = "iban", nullable = false)
    val iban: String,

    /**
     * Deterministic lookup value for [iban] — see [IbanFingerprint] and the class-level KDoc.
     *
     * Nullable because a row written outside [org.commonlink.service.PayeeService.addIban] (a
     * seed script, a future bulk import) won't have one — every row created through `addIban`
     * always does.
     */
    @Column(name = "iban_fingerprint", length = 64)
    val ibanFingerprint: String? = null,

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
    val createdAt: Instant = Instant.now(),

    /**
     * Whether this IBAN can still be used to receive payouts.
     *
     * A [IbanVerificationStatus.VERIFIED] IBAN that has already received a payout cannot be
     * deleted (audit trail) — it can only be disabled via this flag, which excludes it from
     * payout selection ([org.commonlink.service.PayoutService.blockingReasonsFor]).
     */
    @Column(name = "active", nullable = false)
    var active: Boolean = true
)

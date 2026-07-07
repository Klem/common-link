package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Public profile for a non-profit association user.
 *
 * Created at registration time and linked one-to-one with the parent [User].
 * The [identifier] field stores the French SIREN/RNA identifier (9 characters) that
 * uniquely identifies the organisation with public authorities.
 *
 * KYC lifecycle is tracked via [verificationStatus]; documents are stored in [AssociationDocument].
 */
@Entity
@Table(name = "association_profiles")
class AssociationProfile(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The [User] account that owns this profile. The unique constraint enforces one profile per user. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    /** Official registered name of the association. */
    @Column(name = "name", nullable = false)
    val name: String,

    /** French SIREN or RNA identifier (9 characters) for legal identification. */
    @Column(name = "identifier", nullable = false, length = 9)
    val identifier: String,

    /** City where the association is headquartered. */
    @Column(name = "city")
    var city: String? = null,

    /** Postal code of the association's headquarters. */
    @Column(name = "postal_code")
    var postalCode: String? = null,

    /** Name of the primary contact person for the association. */
    @Column(name = "contact_name")
    var contactName: String? = null,

    /** Public description of the association's mission and activities. */
    @Column(name = "description")
    var description: String? = null,

    /** French RNA number (e.g. W123456789). Editable after creation. */
    @Column(name = "rna", length = 20)
    var rna: String? = null,

    /** Year the association was founded (e.g. 2018). */
    @Column(name = "creation_year")
    var creationYear: Short? = null,

    /** Public contact email for the association. */
    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null,

    /** Public contact phone number. */
    @Column(name = "phone", length = 30)
    var phone: String? = null,

    /** KYC verification lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    var verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,

    /** Reason provided by the admin when rejecting the KYC dossier. */
    @Column(name = "verification_rejection_reason", columnDefinition = "TEXT")
    var verificationRejectionReason: String? = null,

    /** Timestamp when the association last submitted their KYC documents. */
    @Column(name = "verification_submitted_at")
    var verificationSubmittedAt: Instant? = null,

    /** Timestamp when an admin approved the KYC dossier. */
    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,
)

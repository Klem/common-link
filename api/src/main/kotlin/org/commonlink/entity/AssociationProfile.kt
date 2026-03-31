package org.commonlink.entity

import jakarta.persistence.*
import java.util.UUID

/**
 * Public profile for a non-profit association user.
 *
 * Created at registration time and linked one-to-one with the parent [User].
 * The [identifier] field stores the French SIREN/RNA identifier (9 characters) that
 * uniquely identifies the organisation with public authorities.
 *
 * The [verified] flag is set by an administrator after manual verification of the
 * organisation's legal status and is used to display a "verified" badge on the platform.
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

    /** Whether an administrator has verified the association's legal status. */
    @Column(name = "verified", nullable = false)
    var verified: Boolean = false
)

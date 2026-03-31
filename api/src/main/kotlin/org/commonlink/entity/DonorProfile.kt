package org.commonlink.entity

import jakarta.persistence.*
import java.util.UUID

/**
 * Public profile for a donor user.
 *
 * Created automatically when a donor account is registered and linked one-to-one
 * with the parent [User]. Donors can choose to appear anonymously on donation
 * listings by setting [anonymous] to `true`.
 */
@Entity
@Table(name = "donor_profiles")
class DonorProfile(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The [User] account that owns this profile. The unique constraint enforces one profile per user. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    /** Optional public name shown on donation listings. Falls back to "Anonymous" when null or when [anonymous] is true. */
    @Column(name = "display_name")
    var displayName: String? = null,

    /** When `true`, the donor's name is hidden on all public donation listings. */
    @Column(name = "anonymous", nullable = false)
    var anonymous: Boolean = false
)

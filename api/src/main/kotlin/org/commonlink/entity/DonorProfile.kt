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
    var anonymous: Boolean = false,

    /** Deterministic 20-byte EVM address derived from the donor's UUID via HMAC-SHA256. Null until first donation. */
    @Column(name = "wallet_address", length = 42)
    var walletAddress: String? = null,

    /** Civil first name of the donor. Distinct from [displayName], which is the public pseudonym. */
    @Column(name = "first_name", length = 128)
    var firstName: String? = null,

    /** Civil last name of the donor. Distinct from [displayName], which is the public pseudonym. */
    @Column(name = "last_name", length = 128)
    var lastName: String? = null,

    /** When `true`, the donor receives the monthly impact report by email. Enabled by default. */
    @Column(name = "notify_monthly_report", nullable = false)
    var notifyMonthlyReport: Boolean = true,

    /** When `true`, the donor is emailed when a supported association publishes a new payout. Enabled by default. */
    @Column(name = "notify_new_payout", nullable = false)
    var notifyNewPayout: Boolean = true,

    /** When `true`, the donor is emailed when a supported campaign reaches its goal. Enabled by default. */
    @Column(name = "notify_goal_reached", nullable = false)
    var notifyGoalReached: Boolean = true,

    /** When `true`, the donor accepts campaign suggestions by email. Opt-in: disabled by default. */
    @Column(name = "notify_suggestions", nullable = false)
    var notifySuggestions: Boolean = false,
)

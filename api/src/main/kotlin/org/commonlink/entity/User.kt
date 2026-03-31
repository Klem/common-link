package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Core identity entity representing a registered user on the platform.
 *
 * A user may be a [UserRole.DONOR] or an [UserRole.ASSOCIATION]. Authentication is
 * multi-provider: a single user row can accumulate a password hash, a Google `sub`, and
 * magic-link usage over time (the [provider] field tracks the original creation method).
 *
 * Sensitive data note: [passwordHash] stores only the BCrypt digest, never the plaintext
 * password. [googleSub] is the immutable Google user ID used to identify returning Google
 * sign-in users.
 */
@Entity
@Table(name = "users")
class User(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** Unique email address, used as the primary login identifier for non-Google flows. */
    @Column(name = "email", nullable = false, unique = true)
    val email: String,

    /** Determines which dashboard and routes the user can access. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: UserRole,

    /** Records how the account was originally created (email, Google, or magic-link). */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    val provider: AuthProvider,

    /** BCrypt hash of the user's password. Null for accounts created via Google or magic-link only. */
    @Column(name = "password_hash")
    var passwordHash: String? = null,

    /** Google account subject identifier. Set when the user signs up or links via Google OAuth. */
    @Column(name = "google_sub")
    var googleSub: String? = null,

    /** Display name pulled from the Google profile or set by the user. */
    @Column(name = "display_name")
    var displayName: String? = null,

    /** Avatar URL pulled from the Google profile. */
    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    /**
     * Whether the user's email address has been confirmed.
     *
     * Set to `true` immediately for Google and magic-link registrations (email ownership is
     * implicit). For email/password registration it starts `false` until the verification
     * link is clicked.
     */
    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    /** Timestamp of account creation; immutable after insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** Timestamp of last profile modification; updated on every save. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

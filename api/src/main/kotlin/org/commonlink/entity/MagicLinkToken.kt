package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * One-time token used for passwordless authentication via a magic link sent to the user's email.
 *
 * Security model: only the SHA-256 hash of the raw token is stored in this table.
 * The raw token is embedded in the link URL sent by email and is never persisted.
 * A token is valid for 15 minutes and can only be used once ([usedAt] is set on first use).
 *
 * For association sign-ups via magic link, the association registration data
 * ([assocName], [assocIdentifier], [assocCity], [assocPostalCode]) is stored alongside the token
 * so the profile can be created when the link is verified — without requiring a prior account.
 */
@Entity
@Table(name = "magic_link_tokens")
class MagicLinkToken(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** Email address the magic link was sent to; used to look up or create the user account on verification. */
    @Column(name = "email", nullable = false)
    val email: String,

    /** SHA-256 hex digest of the raw token. The raw token is never stored. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    /** Role to assign if a new account is created when this token is verified. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: UserRole,

    /** Timestamp after which the token is no longer valid. */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    /** Timestamp when the token was consumed. Null means the token has not been used yet. */
    @Column(name = "used_at")
    var usedAt: Instant? = null,

    /** Official name of the association (captured during magic-link sign-up for [UserRole.ASSOCIATION]). */
    @Column(name = "assoc_name")
    val assocName: String? = null,

    /** French SIREN/RNA identifier of the association (captured during magic-link sign-up). */
    @Column(name = "assoc_identifier", length = 36)
    val assocIdentifier: String? = null,

    /** City of the association (captured during magic-link sign-up). */
    @Column(name = "assoc_city")
    val assocCity: String? = null,

    /** Postal code of the association (captured during magic-link sign-up). */
    @Column(name = "assoc_postal_code", length = 16)
    val assocPostalCode: String? = null,

    /** Timestamp when this token record was created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

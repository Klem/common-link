package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Long-lived token used to obtain a new JWT access token without re-authenticating.
 *
 * Security model: only the SHA-256 hash of the raw token is stored. The raw token is
 * returned to the client once (in the auth response) and is never persisted.
 * Refresh tokens are valid for 30 days and implement token rotation: each use revokes
 * the current token and issues a new one, limiting the damage if a token is stolen.
 *
 * A user may have multiple active refresh tokens (e.g. different devices). All tokens
 * for a user are revoked on logout.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The [User] who owns this token. A user may have multiple refresh tokens (multiple devices/sessions). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    /** SHA-256 hex digest of the raw refresh token. The raw token is never stored. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    /** Timestamp after which the token is no longer valid even if not revoked. */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    /**
     * Whether this token has been explicitly revoked.
     *
     * Set to `true` on use (token rotation), on logout, or on suspicious activity.
     * A revoked token must never be exchanged for a new access token.
     */
    @Column(name = "revoked", nullable = false)
    var revoked: Boolean = false,

    /** Timestamp when this token record was created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * One-time token sent by email to verify that a user owns the address they registered with.
 *
 * Only applies to email/password registrations. Google and magic-link accounts skip this
 * step because email ownership is already proved by the sign-in flow.
 *
 * Security model: only the SHA-256 hash of the raw token is stored. The raw token is
 * embedded in the verification URL emailed to the user and is never persisted.
 * Tokens are valid for 24 hours and can be used only once ([usedAt] is set on first use).
 * Up to 3 verification emails may be sent per 10-minute window (rate-limited in [org.commonlink.service.AuthService]).
 */
@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The [User] whose email address this token verifies. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    /** SHA-256 hex digest of the raw token included in the verification URL. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    /** Timestamp after which the token is no longer valid. */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    /** Timestamp when the token was consumed. Null means the token has not been used yet. */
    @Column(name = "used_at")
    var usedAt: Instant? = null,

    /** Timestamp when this token record was created (used for rate-limiting re-send requests). */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

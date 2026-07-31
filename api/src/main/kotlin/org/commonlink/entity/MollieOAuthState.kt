package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Short-lived CSRF state record for a Mollie Connect OAuth2 authorization request.
 *
 * Created when the association requests the authorization URL; deleted after the
 * callback code exchange succeeds or after [expiresAt] (10-minute TTL).
 * The [state] value is sent to Mollie and echoed back in the callback, allowing
 * the backend to bind the callback to the correct association without browser storage.
 * No PKCE — Mollie Connect uses client_secret (Basic auth) instead.
 */
@Entity
@Table(name = "mollie_oauth_states")
class MollieOAuthState(

    /** Random UUID used as the OAuth2 `state` parameter (CSRF protection). */
    @Id
    @Column(name = "state", nullable = false, updatable = false)
    val state: String,

    /** The association that initiated the authorization request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "association_id", nullable = false)
    val association: AssociationProfile,

    /** Expiry timestamp; records older than this are invalid and should be purged. */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
)

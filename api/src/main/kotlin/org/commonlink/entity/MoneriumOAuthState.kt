package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Short-lived CSRF / PKCE state record for a Monerium OAuth2 authorization request.
 *
 * Created when the association requests the authorization URL; deleted after the
 * callback code exchange succeeds or after [expiresAt] (10-minute TTL).
 * The [state] value is sent to Monerium and echoed back in the callback, allowing
 * the backend to retrieve the correct [codeVerifier] without storing anything in the browser.
 */
@Entity
@Table(name = "monerium_oauth_states")
class MoneriumOAuthState(

    /** Random UUID used as the OAuth2 `state` parameter (CSRF protection). */
    @Id
    @Column(name = "state", nullable = false, updatable = false)
    val state: String,

    /** PKCE S256 code verifier — never sent to the browser, only stored server-side. */
    @Column(name = "code_verifier", nullable = false, columnDefinition = "TEXT")
    val codeVerifier: String,

    /** The association that initiated the authorization request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "association_id", nullable = false)
    val association: AssociationProfile,

    /** Expiry timestamp; records older than this are invalid and should be purged. */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
)

package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Persisted Monerium OAuth2 connection for an association.
 *
 * One-to-one with [AssociationProfile]; holds the access/refresh tokens returned after
 * the PKCE authorization code exchange. The presence of this record means the association
 * has completed Monerium KYC and has an active wallet.
 */
@Entity
@Table(name = "monerium_connections")
class MoneriumConnection(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The association that owns this wallet connection. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "association_id", nullable = false, unique = true)
    val association: AssociationProfile,

    /** Monerium user identifier returned in the token response. */
    @Column(name = "monerium_user_id", nullable = true)
    val moneriumUserId: String?,

    /** Short-lived bearer token for Monerium API calls. */
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    var accessToken: String,

    /** Long-lived token used to obtain a new access token without re-authentication. */
    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    var refreshToken: String,

    /** Timestamp at which the connection was first established. */
    @Column(name = "connected_at", nullable = false)
    val connectedAt: Instant = Instant.now(),

    /** Timestamp at which the current access token expires. */
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    /**
     * Operational state — ACTIVE while the refresh token works, BROKEN once Monerium has
     * rejected a refresh attempt. A BROKEN connection requires user-driven re-authentication
     * (see [org.commonlink.exception.MoneriumReauthRequiredException]).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: MoneriumConnectionState = MoneriumConnectionState.ACTIVE,
)

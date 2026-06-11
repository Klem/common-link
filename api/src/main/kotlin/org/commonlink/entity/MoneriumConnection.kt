package org.commonlink.entity

import jakarta.persistence.*
import org.commonlink.security.MoneriumTokenConverter
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

    /**
     * Monerium profile id (KYB record). Captured at callback time by querying `/profiles`
     * with the new access token; null if the user has no profile yet (brand-new Monerium
     * account with KYB not started). Pinning this here removes the need to re-discover the
     * right profile on every API call when the user happens to own multiple profiles.
     */
    @Column(name = "monerium_profile_id", nullable = true)
    var moneriumProfileId: String? = null,

    /**
     * Display name of the linked Monerium profile. Exposed in [org.commonlink.dto.MoneriumStatusDto]
     * so the frontend can render a "Connected as: X" line with a "not me — reconnect" affordance,
     * which is the only mitigation we have for browser-autofill connecting the wrong account
     * (Monerium doesn't honour `prompt=login`).
     */
    @Column(name = "monerium_profile_name", nullable = true)
    var moneriumProfileName: String? = null,

    /** Short-lived bearer token for Monerium API calls. Encrypted at rest via AES-256-GCM. */
    @Convert(converter = MoneriumTokenConverter::class)
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    var accessToken: String,

    /** Long-lived token used to obtain a new access token without re-authentication. Encrypted at rest via AES-256-GCM. */
    @Convert(converter = MoneriumTokenConverter::class)
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

    /**
     * On-chain wallet address (42-char hex with 0x prefix) fetched from
     * `GET /addresses?profile={profileId}` at OAuth callback time.
     * Null when Monerium KYB is incomplete and no address has been linked yet.
     * Required before a [org.commonlink.entity.OnchainJobAction.VERIFY_ASSOCIATION] job
     * can be enqueued.
     */
    @Column(name = "wallet_address", length = 42)
    var walletAddress: String? = null,

    /**
     * Chain on which [walletAddress] lives, as reported by Monerium at OAuth callback
     * time. Persisted so downstream on-chain dispatch uses the link-time chain as the
     * source of truth rather than re-deriving it.
     */
    @Column(name = "wallet_chain", length = 32)
    var walletChain: String? = null,
)

package org.commonlink.entity

import jakarta.persistence.*
import org.commonlink.security.MoneriumTokenConverter
import java.time.Instant
import java.util.UUID

/**
 * Persisted Mollie Connect OAuth2 connection for an association.
 *
 * One-to-one with [AssociationProfile]; holds the access/refresh tokens returned after the
 * authorization code exchange. The presence of this record means the association has initiated
 * Mollie KYC onboarding. [onboardingStatus] tracks KYC progress and is refreshed throttled
 * (at most once every 5 minutes) on each status read.
 */
@Entity
@Table(name = "mollie_connections")
class MollieConnection(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The association that owns this Mollie connection. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "association_id", nullable = false, unique = true)
    val association: AssociationProfile,

    /** Short-lived bearer token for Mollie API calls. Encrypted at rest via AES-256-GCM. */
    @Convert(converter = MoneriumTokenConverter::class)
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    var accessToken: String,

    /** Long-lived token used to obtain a new access token without re-authorization. Encrypted at rest via AES-256-GCM. */
    @Convert(converter = MoneriumTokenConverter::class)
    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    var refreshToken: String,

    /** Timestamp at which the connection was first established. */
    @Column(name = "connected_at", nullable = false)
    val connectedAt: Instant = Instant.now(),

    /** Timestamp at which the current access token expires. Updated on every token refresh. */
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    /**
     * Operational state — ACTIVE while the refresh token works, BROKEN once Mollie has
     * rejected a refresh attempt (invalid_grant). A BROKEN connection requires user-driven
     * re-authorization via the OAuth popup.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: MollieConnectionState = MollieConnectionState.ACTIVE,

    /**
     * KYC onboarding status as reported by Mollie's GET /v2/onboarding/me.
     * Refreshed throttled on status reads when not yet COMPLETED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false, length = 16)
    var onboardingStatus: MollieOnboardingStatus = MollieOnboardingStatus.NEEDS_DATA,

    /** Whether Mollie has authorized this merchant to receive payments. */
    @Column(name = "can_receive_payments", nullable = false)
    var canReceivePayments: Boolean = false,

    /** Whether Mollie has authorized this merchant to receive settlements. */
    @Column(name = "can_receive_settlements", nullable = false)
    var canReceiveSettlements: Boolean = false,

    /**
     * Mollie organization id (org_…). Captured at callback time via GET /v2/organizations/me.
     * Enforces the uniqueness guard: one Mollie organization can be linked to at most one association.
     */
    @Column(name = "mollie_organization_id")
    var mollieOrganizationId: String? = null,

    /** Last time onboarding status was synced from Mollie. Used for 5-minute throttle. */
    @Column(name = "last_synced_at")
    var lastSyncedAt: Instant? = null,

    /**
     * Deep link to the Mollie hosted onboarding wizard (_links.dashboard of GET /v2/onboarding/me).
     * Mollie only returns it while onboarding is incomplete; synced alongside [onboardingStatus].
     * The frontend opens it in a new tab when the status is NEEDS_DATA.
     */
    @Column(name = "onboarding_dashboard_url", columnDefinition = "TEXT")
    var onboardingDashboardUrl: String? = null,
)

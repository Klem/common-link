package org.commonlink.entity

import jakarta.persistence.*
import org.commonlink.security.OAuthTokenConverter
import java.time.Instant
import java.util.UUID

/**
 * Link between an association and its HelloAsso account, in API keys mode (the association's own `client_id` /
 * `client_secret`).
 *
 * One-to-one with [AssociationProfile], kept when the association moves to another payment provider and on
 * disconnection ([HelloAssoConnectionState.DISCONNECTED]): history and [webhookToken] survive. The row is created
 * before the link, so the association can be shown its notification URL ([webhookToken]) while it links its account;
 * every link field is null while [state] is [HelloAssoConnectionState.NOT_LINKED].
 *
 * **The `client_secret` is never stored** — used once to obtain the tokens, then discarded ([secretWipedAt] records
 * when). Only [clientId] (not a secret) and the tokens are kept, the tokens encrypted at rest.
 */
@Entity
@Table(name = "helloasso_connections")
class HelloAssoConnection(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The association that owns this HelloAsso link. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "association_id", nullable = false, unique = true)
    val association: AssociationProfile,

    /** Opaque token of the association's notification URL (`/api/public/webhooks/helloasso/{token}`). Never changes. */
    @Column(name = "webhook_token", nullable = false, unique = true, length = 64, updatable = false)
    val webhookToken: String,

    /** Link lifecycle; see [HelloAssoConnectionState]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: HelloAssoConnectionState = HelloAssoConnectionState.NOT_LINKED,

    /** HelloAsso organization slug the keys belong to; null until linked. */
    @Column(name = "organization_slug")
    var organizationSlug: String? = null,

    /** The association's HelloAsso API `client_id` (not a secret); null until linked. */
    @Column(name = "client_id")
    var clientId: String? = null,

    /** HelloAsso refresh token (rotated on every refresh, valid one month). Encrypted at rest; null until linked and after disconnection. */
    @Convert(converter = OAuthTokenConverter::class)
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    var refreshToken: String? = null,

    /** Cached HelloAsso access token (30 min). Encrypted at rest; null when not cached. */
    @Convert(converter = OAuthTokenConverter::class)
    @Column(name = "access_token", columnDefinition = "TEXT")
    var accessToken: String? = null,

    /** Expiry of [accessToken]. */
    @Column(name = "access_token_expires_at")
    var accessTokenExpiresAt: Instant? = null,

    /** When the `client_secret` used for the link was discarded (it is never persisted). */
    @Column(name = "secret_wiped_at")
    var secretWipedAt: Instant? = null,

    /** Last read of HelloAsso's `isCashInCompliant`: whether the organization can collect right now. */
    @Column(name = "can_collect", nullable = false)
    var canCollect: Boolean = false,

    /** Whether the organization was ever cash-in compliant — distinguishes "pending verification" from "suspended". */
    @Column(name = "ever_compliant", nullable = false)
    var everCompliant: Boolean = false,

    /** Last eligibility read (`isCashInCompliant`). */
    @Column(name = "last_eligibility_check_at")
    var lastEligibilityCheckAt: Instant? = null,

    /**
     * Last notification received on [webhookToken] that belonged to CommonLink: an `Order` / `Payment` of one of its
     * donations, or an `Organization` event received on this URL. A foreign or unreadable notification is never
     * recorded.
     */
    @Column(name = "webhook_last_received_at")
    var webhookLastReceivedAt: Instant? = null,

    /** Last successful token refresh. */
    @Column(name = "last_refresh_at")
    var lastRefreshAt: Instant? = null,

    /** When the account was linked. */
    @Column(name = "linked_at")
    var linkedAt: Instant? = null,

    /** User who linked the account (`users.id`); null if that user was deleted. */
    @Column(name = "linked_by")
    var linkedBy: UUID? = null,

    /** Last time the reconciler confirmed a payment whose notification never arrived (V89). */
    @Column(name = "webhook_missing_at")
    var webhookMissingAt: Instant? = null,

    /** When the association started the "no HelloAsso account yet" journey (V89); drives the D+1 / D+7 reminders. */
    @Column(name = "onboarding_started_at")
    var onboardingStartedAt: Instant? = null,

    /** D+1 reminder sent (V89). */
    @Column(name = "reminder_j1_sent_at")
    var reminderJ1SentAt: Instant? = null,

    /** D+7 reminder sent (V89). */
    @Column(name = "reminder_j7_sent_at")
    var reminderJ7SentAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** Set by every writer on modification. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

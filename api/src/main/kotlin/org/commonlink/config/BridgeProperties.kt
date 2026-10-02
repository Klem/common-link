package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Bridge API configuration, bound from the `app.bridge` prefix.
 *
 * Bridge initiates the real outgoing SEPA transfer of a confirmed payout through Open Banking: the
 * association authorises it with its own bank and the funds go straight to the payee IBAN. Its
 * credentials are server-side only — Bridge's own documentation requires that the secret reach
 * "only your servers", so nothing here may ever be mirrored into a `NEXT_PUBLIC_*` frontend
 * variable.
 *
 * Every value defaults to a safe blank so the application boots with zero environment setup in
 * local development, where [demoMode] is on and no credential is read.
 *
 * @param demoMode When true, transfers are simulated in-process and Bridge is never called.
 *   Defaults to true: the Payments tab must work end-to-end before any Bridge credential exists.
 *   Production accepts it too — `application-prod.yml` binds `${BRIDGE_DEMO_MODE:true}` — because
 *   the production Bridge credentials are not provisioned yet and a prod boot must not depend on
 *   variables nobody can set. Every credential below therefore tolerates a blank, and
 *   [org.commonlink.service.BridgePaymentInitiationService] fails fast at startup if demo mode is
 *   switched off while they are still blank.
 * @param baseUrl Bridge API root. Sandbox and production share this host and differ only by
 *   credentials — hence the explicit [demoMode] guard rather than a URL-based heuristic.
 * @param apiVersion Value of the mandatory `Bridge-Version` header.
 * @param clientId Value of the `Client-Id` header.
 * @param clientSecret Value of the `Client-Secret` header. Never log it.
 * @param connectTimeout TCP connect timeout for Bridge calls.
 * @param readTimeout Response read timeout for Bridge calls. Creating an initiation that hangs
 *   must fail fast: a user is waiting on the bank-authorisation URL.
 * @param webhookSecret Current signing secret for the `BridgeApi-Signature` webhook header
 *   (HMAC-SHA256 over the raw body), retrieved from the Bridge dashboard when the webhook is
 *   created. Blank means verification is skipped with a warning — see
 *   [org.commonlink.service.BridgeWebhookSignatureVerifier].
 * @param webhookSecretPrevious Prior signing secret, kept valid by Bridge for 24h after a
 *   rotation. Both are checked so a rotation never causes a silent verification gap.
 * @param linkValidity How long a payment link stays authorisable, sent as Bridge's `expired_date`.
 *   Bridge's own default is 15 minutes when the field is omitted, which is short for an
 *   association that has to authenticate at its bank; a day is the working figure. Configurable
 *   because the expiry path — does Bridge notify, and does the payout come back to a retryable
 *   PENDING — is otherwise a 24-hour feedback loop: set a handful of minutes on staging and the
 *   same scenario is observable immediately. It bounds how long a payout's amount can stay
 *   engaged on the campaign, so it is never zero.
 * @param releaseGrace How recently a payout's Bridge state must have moved for a dead link to
 *   leave it engaged instead of releasing it — see
 *   [org.commonlink.service.BridgeWebhookService].
 *
 *   Bridge stamps `ACTC` the moment the payer enters the tunnel and holds it for the whole bank
 *   authentication, which is where the race lives: if the link expires in that window the payout
 *   is released, its amount handed back to the campaign, and the transfer then settles anyway —
 *   `PAYOUT_SETTLED_AFTER_RELEASE`, with the association free to have spent the returned balance
 *   in between. Sized to cover a strong authentication (app notification, code, card reader) and
 *   no more, because everything it covers is an amount held a little longer for nothing in the
 *   common case. Zero disables the deferral.
 */
@ConfigurationProperties(prefix = "app.bridge")
data class BridgeProperties(
    val demoMode: Boolean = true,
    val baseUrl: String = "https://api.bridgeapi.io",
    val apiVersion: String = "2025-01-15",
    val clientId: String = "",
    val clientSecret: String = "",
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(15),
    val webhookSecret: String = "",
    val webhookSecretPrevious: String = "",
    val linkValidity: Duration = Duration.ofDays(1),
    val releaseGrace: Duration = Duration.ofMinutes(3),
    val reconciler: Reconciler = Reconciler(),
) {
    /**
     * Settings of [org.commonlink.service.BridgePayoutReconciler].
     *
     * @param enabled Whether the sweep runs at all. On by default: without it a notification
     *   accepted and not applied strands a payout for good, since Bridge never re-sends what it
     *   considers delivered.
     * @param fixedDelay Gap between the end of one sweep and the start of the next.
     * @param initialDelay Grace period after boot, so a restart does not sweep before the
     *   application is warm.
     * @param staleAfter How long a payout may sit engaged without news before it is re-read.
     *   Comfortably longer than a normal settlement, which Bridge describes as "typically within
     *   one open day", so an ordinary transfer is never re-read.
     * @param stuckAfter How long past authorisation a payout may stay unresolved before a human is
     *   asked to confirm it from the bank statement. Never promotes anything by itself: the
     *   on-chain attestation is irretractable.
     */
    data class Reconciler(
        val enabled: Boolean = true,
        val fixedDelay: Duration = Duration.ofMinutes(30),
        val initialDelay: Duration = Duration.ofMinutes(2),
        val staleAfter: Duration = Duration.ofHours(6),
        val stuckAfter: Duration = Duration.ofDays(3),
    )
}

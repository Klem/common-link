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
)

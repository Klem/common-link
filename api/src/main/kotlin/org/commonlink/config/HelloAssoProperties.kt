package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * HelloAsso configuration (API keys mode), bound from the `app.helloasso` prefix.
 *
 * **No `enabled` flag** (decision 2026-11-02): HelloAsso beans are always loaded; whether an association uses
 * HelloAsso is its [org.commonlink.entity.AssociationProfile.paymentProvider]. Only the mock mode ([mock]) is
 * conditional, and it can never be active under the `prod` profile.
 *
 * Defaults point to the **sandbox**, so a missing variable can never make a real call to the production API;
 * `application-prod.yml` sets the production URLs and notification IP explicitly.
 *
 * @param apiBaseUrl HelloAsso API base URL, version included (prod `https://api.helloasso.com/v5`).
 * @param oauthTokenUrl HelloAsso OAuth2 token endpoint (prod `https://api.helloasso.com/oauth2/token`).
 * @param allowedNotificationIps Source IPs HelloAsso sends notifications from (prod `51.138.206.200`, sandbox
 *   `4.233.135.234`); any other source is refused.
 * @param mock Local / E2E mode without HelloAsso network calls. MUST stay false in production.
 * @param notificationBaseUrl Public base URL of this API, prefix of each association's notification URL
 *   (`{notificationBaseUrl}/api/public/webhooks/helloasso/{token}`). Blank = not configured.
 * @param tokenRefreshInterval Cadence of the scheduled token refresh (a refresh token lives one month).
 * @param eligibilityPendingInterval Eligibility (`isCashInCompliant`) read cadence while an organization cannot
 *   collect yet.
 * @param eligibilityReadyInterval Eligibility read cadence once an organization can collect.
 * @param reconcilerStaleAfter Age after which a pending `helloasso:` donation is re-read at HelloAsso.
 * @param checkoutAbandonAfter Age after which an unpaid HelloAsso checkout is considered abandoned (its payment page
 *   only lives 15 minutes, but a payment may still be confirmed late).
 * @param eligibilityRefreshRateLimit Minimum interval between two eligibility refreshes requested by the same
 *   association ("Vérifier mon dossier").
 */
@ConfigurationProperties(prefix = "app.helloasso")
data class HelloAssoProperties(
    val apiBaseUrl: String = "https://api.helloasso-sandbox.com/v5",
    val oauthTokenUrl: String = "https://api.helloasso-sandbox.com/oauth2/token",
    val allowedNotificationIps: List<String> = listOf("4.233.135.234"),
    val mock: Boolean = false,
    val notificationBaseUrl: String = "",
    val tokenRefreshInterval: Duration = Duration.ofDays(7),
    val eligibilityPendingInterval: Duration = Duration.ofHours(2),
    val eligibilityReadyInterval: Duration = Duration.ofDays(1),
    val reconcilerStaleAfter: Duration = Duration.ofMinutes(15),
    val checkoutAbandonAfter: Duration = Duration.ofMinutes(45),
    val eligibilityRefreshRateLimit: Duration = Duration.ofMinutes(1),
)

package org.commonlink.exception

import org.springframework.http.HttpStatusCode

/**
 * Failure of a Mollie Connect `grant_type=refresh_token` exchange.
 *
 * Both subtypes extend [IllegalStateException] on purpose: every existing caller of
 * [org.commonlink.service.MollieConnectTokenManager.getValidAccessToken] already catches
 * `IllegalStateException` (`PublicWidgetService.resolveMollieToken` turns it into a 409,
 * `MollieConnectService.refreshOnboardingStatusIfStale` swallows it), so introducing the
 * distinction changes no existing behaviour. Only the scheduled refresh looks at the subtype.
 *
 * @param message Human-readable cause. Never contains a token.
 */
sealed class MollieRefreshException(message: String) : IllegalStateException(message)

/**
 * Mollie has **definitively** rejected the refresh token — typically HTTP 400 `invalid_grant`
 * (revoked authorisation, deleted organisation, refresh token already rotated elsewhere).
 *
 * Retrying cannot help: the association must re-authorise through the OAuth popup. This is the
 * only failure that justifies persisting [org.commonlink.entity.MollieConnectionState.BROKEN].
 *
 * @param status Status code returned by Mollie's `/oauth2/tokens` endpoint.
 * @param body   Raw error body. Mollie returns only an error code here (e.g. `{"error":"invalid_grant"}`),
 *               never a credential — safe to log and to keep for diagnosis.
 */
class MollieRefreshRejectedException(
    val status: HttpStatusCode,
    val body: String,
) : MollieRefreshException("Mollie refresh token rejected (status=$status)")

/**
 * The refresh could not be completed for a reason that says nothing about the grant's validity —
 * throttling (429), a Mollie outage (5xx), a timeout or any I/O error.
 *
 * The connection must stay [org.commonlink.entity.MollieConnectionState.ACTIVE]: marking it BROKEN
 * would turn a transient Mollie hiccup into a mandatory re-onboarding for every association at once.
 * The next scheduled tick retries.
 *
 * @param status Status code returned by Mollie, or null when the call never got a response.
 */
class MollieRefreshUnavailableException(
    val status: HttpStatusCode? = null,
    message: String = "Mollie refresh temporarily unavailable (status=$status)",
) : MollieRefreshException(message)

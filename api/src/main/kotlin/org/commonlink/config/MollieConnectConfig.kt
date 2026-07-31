package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Which Mollie API to poll for KYC onboarding status. */
enum class OnboardingApi {
    /** Deprecated `GET /v2/onboarding/me` — works today, simple status + canReceivePayments. */
    LEGACY,
    /** Beta `GET /v2/capabilities` — requires Mollie feature-flag; 403 until enabled. */
    CAPABILITIES,
}

/**
 * Mollie Connect OAuth2 configuration, bound from the `app.mollie.connect` prefix.
 *
 * Distinct from [MollieProperties] (payment API key) — these credentials are for the
 * OAuth2 KYC onboarding flow only. Registered via [org.springframework.boot.context.properties.EnableConfigurationProperties]
 * on the main application class.
 *
 * @param clientId Mollie OAuth2 app client identifier.
 * @param clientSecret Mollie OAuth2 app client secret (HTTP Basic auth on the /oauth2/tokens endpoints only).
 * @param redirectUri Backend callback URL registered in the Mollie developer console.
 * @param scopes Space-separated OAuth2 scopes requested during authorization.
 * @param advancedToken Organization Advanced access token (Bearer) with the clients.write permission,
 *                      provisioned in the Mollie dashboard. Required to create client links.
 * @param mock When true, skips Mollie entirely and creates a mock COMPLETED connection locally.
 * @param allowFakeCompletion Dev/staging escape hatch. When true, exposes an endpoint that flips an
 *                            EXISTING real connection to COMPLETED — as if Mollie had just validated
 *                            the KYC — without touching Mollie. Unlike [mock] it does NOT bypass the
 *                            OAuth popup / client-link creation: the connection must already exist.
 *                            MUST stay false in production. When false the mock controller bean is
 *                            not registered (`@ConditionalOnProperty havingValue="true"`), so the
 *                            route returns 404. `application-prod.yml` sets it false explicitly.
 * @param onboardingApi Which Mollie API to use for KYC status polling. Defaults to [OnboardingApi.LEGACY]
 *                      (`GET /v2/onboarding/me`) which works today. Switch to [OnboardingApi.CAPABILITIES]
 *                      (`GET /v2/capabilities`) once Mollie enables the feature flag for this org.
 */
@ConfigurationProperties(prefix = "app.mollie.connect")
data class MollieConnectConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: String,
    val advancedToken: String = "",
    val mock: Boolean = false,
    val allowFakeCompletion: Boolean = false,
    val onboardingApi: OnboardingApi = OnboardingApi.LEGACY,
) {
    /** Redacts [clientSecret] and [advancedToken] (Bearer with clients.write) so they never leak via logs. */
    override fun toString(): String =
        "MollieConnectConfig(clientId=$clientId, clientSecret=***, redirectUri=$redirectUri, " +
        "scopes=$scopes, advancedToken=***, mock=$mock, allowFakeCompletion=$allowFakeCompletion, " +
        "onboardingApi=$onboardingApi)"
}

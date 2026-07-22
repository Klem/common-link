package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Mollie Connect OAuth2 configuration, bound from the `app.mollie.connect` prefix.
 *
 * Distinct from [MollieProperties] (payment API key) — these credentials are for the
 * OAuth2 KYC onboarding flow only. Registered via [org.springframework.boot.context.properties.EnableConfigurationProperties]
 * on the main application class.
 *
 * @param clientId Mollie OAuth2 app client identifier.
 * @param clientSecret Mollie OAuth2 app client secret (Basic auth on token exchange).
 * @param organizationToken Organization access token with `clients.write` permission — NOT the payment API key.
 * @param redirectUri Backend callback URL registered in the Mollie developer console.
 * @param scopes Space-separated OAuth2 scopes requested during authorization.
 * @param mock When true, skips Mollie entirely and creates a mock COMPLETED connection locally.
 */
@ConfigurationProperties(prefix = "app.mollie.connect")
data class MollieConnectConfig(
    val clientId: String,
    val clientSecret: String,
    val organizationToken: String,
    val redirectUri: String,
    val scopes: String,
    val mock: Boolean = false,
)

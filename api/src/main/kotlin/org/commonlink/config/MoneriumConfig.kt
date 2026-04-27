package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Monerium OAuth2 PKCE integration settings, bound from the `app.monerium` prefix.
 *
 * No client secret — PKCE (S256) replaces the secret in the authorization code flow.
 */
@Configuration
@ConfigurationProperties(prefix = "app.monerium")
data class MoneriumConfig(
    /** Monerium OAuth2 client identifier. */
    val clientId: String = "",
    /** Monerium API base URL (sandbox or production). */
    val baseUrl: String = "https://sandbox.monerium.app",
    /** Backend callback URL registered in the Monerium developer console. */
    val redirectUri: String = "",
)

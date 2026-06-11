package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Monerium OAuth2 PKCE integration settings, bound from the `app.monerium` prefix.
 *
 * No client secret — PKCE (S256) replaces the secret in the authorization code flow.
 * Registered via {@link org.springframework.boot.context.properties.EnableConfigurationProperties}
 * on the main application class to avoid CGLIB proxying (which would require mutable setters).
 */
@ConfigurationProperties(prefix = "app.monerium")
data class MoneriumConfig(
    /** Monerium OAuth2 client identifier. */
    val clientId: String = "",
    /** Monerium API base URL (sandbox or production). */
    val baseUrl: String = "https://sandbox.monerium.app",
    /** Backend callback URL registered in the Monerium developer console. */
    val redirectUri: String = "",

    val skipKyc: String = "false",

    /**
     * Base64-encoded 32-byte AES-256 key for encrypting OAuth tokens at rest.
     * Required — no default. Set via MONERIUM_TOKEN_ENC_KEY env var.
     * Generate: openssl rand -base64 32
     */
    val tokenEncKey: String = "",
)

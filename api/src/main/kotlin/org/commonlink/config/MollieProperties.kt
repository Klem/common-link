package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Mollie payment gateway configuration, bound from the `mollie` prefix.
 *
 * Supply all values via environment variables in non-local environments.
 * Never commit a live API key.
 *
 * @param apiKey Mollie API key (`test_xxx` for sandbox, `live_xxx` for production).
 * @param apiBaseUrl Mollie REST API base URL.
 * @param redirectBaseUrl Frontend base URL used to build the post-payment redirect/cancel URLs.
 * @param webhookUrl Publicly reachable URL of the Mollie webhook endpoint on this API server.
 */
@ConfigurationProperties(prefix = "mollie")
data class MollieProperties(
    val apiKey: String = "",
    val apiBaseUrl: String = "https://api.mollie.com/v2",
    val redirectBaseUrl: String = "http://localhost:3000",
    val webhookUrl: String = "http://localhost:8080/api/public/webhooks/mollie"
)

package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.commonlink.config.HelloAssoProperties
import org.commonlink.exception.HelloAssoException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/**
 * Tokens returned by HelloAsso's OAuth2 endpoint.
 *
 * @property accessToken bearer token for API calls (about 30 minutes).
 * @property refreshToken single-use token to obtain the next pair (about one month); rotated on every refresh.
 * @property expiresIn [accessToken] lifetime in seconds.
 */
data class HelloAssoTokens(val accessToken: String, val refreshToken: String, val expiresIn: Long) {
    /** Never prints the tokens. */
    override fun toString(): String = "HelloAssoTokens(expiresIn=$expiresIn)"
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class HelloAssoTokenResponse(
    @field:JsonProperty("access_token") val accessToken: String?,
    @field:JsonProperty("refresh_token") val refreshToken: String?,
    @field:JsonProperty("expires_in") val expiresIn: Long?,
)

/**
 * Single HTTP layer towards HelloAsso (API keys mode): token issuance and renewal, then authenticated calls.
 * No database access — token caching lives in [HelloAssoTokenManager].
 *
 * **Secrets** — the `client_secret`, the tokens and HelloAsso's response bodies (which may echo them) are never
 * logged nor put in an exception message: logs and [HelloAssoException]s carry only the operation and the status.
 *
 * **User-Agent** — every request carries [USER_AGENT]: HelloAsso refused a token request sent with a generic library
 * User-Agent in sandbox (403), and accepted the same request identifying CommonLink (2026-11-02).
 *
 * **Limits** — every token call first goes through [HelloAssoAuthRateLimiter] and is never retried on 429.
 * Authenticated calls are retried at most [MAX_RETRIES] times on 429, with growing delays.
 */
@Service
class HelloAssoClient(
    private val properties: HelloAssoProperties,
    restClientBuilder: RestClient.Builder,
    private val authRateLimiter: HelloAssoAuthRateLimiter,
) {
    private val log = LoggerFactory.getLogger(HelloAssoClient::class.java)

    private val restClient: RestClient = restClientBuilder
        .defaultHeader("User-Agent", USER_AGENT)
        .build()

    /** Sleeps between 429 retries; replaced in tests to avoid waiting. */
    internal var sleeper: (Duration) -> Unit = { Thread.sleep(it.toMillis()) }

    companion object {
        /** Identifies CommonLink to HelloAsso on every request. */
        const val USER_AGENT = "CommonLink/1.0 (+https://www.common-link.org)"

        /** Retries of an authenticated call answered 429. */
        const val MAX_RETRIES = 2

        /** Delay before retry n (1-based): 1 s, then 2 s. */
        fun retryDelay(attempt: Int): Duration = Duration.ofSeconds(attempt.toLong())
    }

    /**
     * Exchanges the association's API keys for a token pair (`grant_type=client_credentials`). The secret is used for
     * this single call and never kept.
     *
     * @throws HelloAssoUnauthorizedException when HelloAsso rejects the keys.
     */
    fun obtainTokens(clientId: String, clientSecret: String): HelloAssoTokens =
        tokenCall(
            "client_credentials",
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "client_credentials")
                add("client_id", clientId)
                add("client_secret", clientSecret)
            },
        )

    /**
     * Renews a token pair (`grant_type=refresh_token` and the refresh token only, as documented by HelloAsso). The
     * previous refresh token becomes unusable.
     *
     * @throws HelloAssoUnauthorizedException when the refresh token is rejected — the chain is broken.
     */
    fun refreshTokens(refreshToken: String): HelloAssoTokens =
        tokenCall(
            "refresh_token",
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
            },
        )

    /**
     * Authenticated `GET {api-base-url}{path}`.
     *
     * @throws HelloAssoException on any failure (see the subclasses).
     */
    fun <T : Any> get(path: String, accessToken: String, type: Class<T>): T =
        authenticated("GET $path") {
            restClient.get()
                .uri(properties.apiBaseUrl + path)
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(type)
        }

    /**
     * Authenticated `POST {api-base-url}{path}` with a JSON body.
     *
     * @throws HelloAssoException on any failure (see the subclasses).
     */
    fun <T : Any> post(path: String, accessToken: String, body: Any, type: Class<T>): T =
        authenticated("POST $path") {
            restClient.post()
                .uri(properties.apiBaseUrl + path)
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(type)
        }

    private fun tokenCall(operation: String, form: LinkedMultiValueMap<String, String>): HelloAssoTokens {
        authRateLimiter.acquire(operation)
        val response = try {
            restClient.post()
                .uri(properties.oauthTokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(HelloAssoTokenResponse::class.java)
        } catch (ex: RestClientResponseException) {
            val status = ex.statusCode.value()
            log.warn("HelloAsso token call {} failed with HTTP {}", operation, status)
            throw when {
                // OAuth2 reports a rejected grant or client as 400 invalid_grant / 401 invalid_client.
                status == 400 || status == 401 -> HelloAssoUnauthorizedException(operation, status)
                status == 429 -> HelloAssoRateLimitedException(operation)
                else -> HelloAssoUnavailableException(operation, status)
            }
        } catch (ex: RestClientException) {
            log.warn("HelloAsso token call {} failed: {}", operation, ex.javaClass.simpleName)
            throw HelloAssoUnavailableException(operation)
        }
        val accessToken = response?.accessToken
        val refreshToken = response?.refreshToken
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() || response.expiresIn == null) {
            log.warn("HelloAsso token call {} returned an incomplete response", operation)
            throw HelloAssoUnavailableException(operation)
        }
        return HelloAssoTokens(accessToken, refreshToken, response.expiresIn)
    }

    private fun <T : Any> authenticated(operation: String, call: () -> T?): T {
        var attempt = 0
        while (true) {
            try {
                return call() ?: run {
                    log.warn("HelloAsso {} returned an empty response", operation)
                    throw HelloAssoUnavailableException(operation)
                }
            } catch (ex: RestClientResponseException) {
                val status = ex.statusCode.value()
                if (status == HttpStatus.TOO_MANY_REQUESTS.value() && attempt < MAX_RETRIES) {
                    attempt++
                    log.info("HelloAsso {} answered 429, retry {}/{}", operation, attempt, MAX_RETRIES)
                    sleeper(retryDelay(attempt))
                    continue
                }
                log.warn("HelloAsso {} failed with HTTP {}", operation, status)
                throw when (status) {
                    401 -> HelloAssoUnauthorizedException(operation, status)
                    409 -> HelloAssoNotEligibleException(operation)
                    429 -> HelloAssoRateLimitedException(operation)
                    else -> HelloAssoUnavailableException(operation, status)
                }
            } catch (ex: RestClientException) {
                log.warn("HelloAsso {} failed: {}", operation, ex.javaClass.simpleName)
                throw HelloAssoUnavailableException(operation)
            }
        }
    }
}

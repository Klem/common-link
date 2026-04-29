package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonProperty
import org.commonlink.config.MoneriumConfig
import org.commonlink.dto.MoneriumStatusDto
import org.commonlink.entity.MoneriumConnection
import org.commonlink.entity.MoneriumOAuthState
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.commonlink.repository.MoneriumOAuthStateRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Business logic for the Monerium OAuth2 PKCE wallet onboarding flow.
 *
 * The code_verifier is generated and stored server-side in [MoneriumOAuthState] — it never
 * touches the browser. The state UUID is the DB record key and doubles as CSRF protection.
 */
@Service
class MoneriumService(
    private val config: MoneriumConfig,
    private val stateRepo: MoneriumOAuthStateRepository,
    private val connectionRepo: MoneriumConnectionRepository,
    private val associationRepo: AssociationProfileRepository,
    private val restTemplate: RestTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Builds a Monerium authorization URL with PKCE (S256) and persists the OAuth state.
     *
     * @param userId UUID of the user requesting association profile.
     * @return Full Monerium OAuth2 authorization URL to redirect the popup to.
     */
    fun buildAuthorizationUrl(userId: UUID): String {
        val association = associationRepo.findByUserId(userId)
            .orElseThrow { IllegalArgumentException("Association not found for user: $userId") }

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = UUID.randomUUID().toString()

        stateRepo.save(
            MoneriumOAuthState(
                state = state,
                codeVerifier = codeVerifier,
                association = association,
                expiresAt = Instant.now().plusSeconds(600),
            )
        )

        val params = mapOf(
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "response_type" to "code",
            "scope" to "openid",
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "skip_kyc" to config.skipKyc,
            "email" to association.user.email
        ).entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

        logger.info("Generated Monerium auth URL for association {}", association.id)
        return "${config.baseUrl}/auth?$params"
    }

    /**
     * Exchanges the authorization code for tokens, saves the [MoneriumConnection], and
     * deletes the consumed [MoneriumOAuthState] to prevent replay attacks.
     *
     * @param code Authorization code returned by Monerium.
     * @param state State UUID echoed back by Monerium; used to retrieve the code_verifier.
     * @return The persisted [MoneriumConnection].
     * @throws IllegalArgumentException if [state] is unknown.
     * @throws IllegalStateException if [state] has expired or the token response is empty.
     */
    fun handleCallback(code: String, state: String): MoneriumConnection {
        val oauthState = stateRepo.findById(state).orElseThrow {
            logger.warn("Unknown or expired Monerium OAuth state: {}", state)
            IllegalArgumentException("Invalid OAuth state")
        }

        if (oauthState.expiresAt.isBefore(Instant.now())) {
            stateRepo.delete(oauthState)
            throw IllegalStateException("OAuth state expired")
        }

        val tokenResponse = exchangeCode(code, oauthState.codeVerifier)

        val connection = MoneriumConnection(
            association = oauthState.association,
            moneriumUserId = tokenResponse.userId,
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong()),
        )

        stateRepo.delete(oauthState)
        val saved = connectionRepo.save(connection)
        logger.info("Monerium connection saved for association {}", oauthState.association.id)
        return saved
    }

    /**
     * Returns the Monerium connection status for the association.
     *
     * [MoneriumStatusDto.connected] is true when a [MoneriumConnection] record exists.
     * [MoneriumStatusDto.pending] is true when an OAuth flow was started but the code exchange
     * has not completed yet (non-expired [MoneriumOAuthState] record exists).
     *
     * @param userId UUID of the user's association profile.
     */
    fun getConnectionStatus(userId: UUID): MoneriumStatusDto {
        val association = associationRepo.findByUserId(userId)
            .orElseThrow { IllegalArgumentException("Association not found for user: $userId") }
        val connected = connectionRepo.findByAssociation(association) != null
        val pending = !connected && stateRepo.existsByAssociationAndExpiresAtAfter(association, Instant.now())
        return MoneriumStatusDto(connected = connected, pending = pending)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun exchangeCode(code: String, codeVerifier: String): TokenResponse {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", config.clientId)
            add("code", code)
            add("redirect_uri", config.redirectUri)
            add("code_verifier", codeVerifier)
        }
        val response = restTemplate.postForEntity(
            "${config.baseUrl}/auth/token",
            HttpEntity(body, headers),
            TokenResponse::class.java,
        )
        return response.body ?: throw IllegalStateException("Empty token response from Monerium")
    }

    /** Token response payload from POST {baseUrl}/auth/token. */
    data class TokenResponse(
        @field:JsonProperty("access_token") val accessToken: String,
        @field:JsonProperty("refresh_token") val refreshToken: String,
        @field:JsonProperty("expires_in") val expiresIn: Int,
        @field:JsonProperty("user_id") val userId: String?,
    )
}

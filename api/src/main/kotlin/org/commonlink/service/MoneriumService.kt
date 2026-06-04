package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonProperty
import org.commonlink.config.MoneriumConfig
import org.commonlink.config.OnchainConfig
import org.commonlink.dto.MoneriumStatusDto
import org.commonlink.dto.monerium.MoneriumAddressListDto
import org.commonlink.dto.monerium.MoneriumAuthContextDto
import org.commonlink.dto.monerium.MoneriumProfileDto
import org.commonlink.entity.MoneriumConnection
import org.commonlink.entity.MoneriumConnectionState
import org.commonlink.entity.MoneriumOAuthState
import org.commonlink.entity.MoneriumProfileKind
import org.commonlink.exception.ConflictException
import org.commonlink.exception.MoneriumReauthRequiredException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.commonlink.repository.MoneriumOAuthStateRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
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
    private val onchainConfig: OnchainConfig,
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

        // No `email` param on purpose: Monerium pre-fills the login form with whatever we
        // send, which makes the popup look like it's stuck on the previous user's account.
        // Letting the field start empty forces the user to type the credentials they want.
        // Monerium also publishes no logout endpoint, so this is the only knob we have to
        // discourage account-reuse on shared browsers.
        val params = mapOf(
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "response_type" to "code",
            "scope" to "openid",
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "skip_kyc" to config.skipKyc,
            // Standard OIDC param to force re-authentication; Monerium does NOT document
            // honouring it today, but sending it costs nothing and we get the fix for free
            // if/when they add support. Real defence against wrong-account binding is the
            // post-callback profile capture + UI "Connected as: X" confirmation.
            "prompt" to "login",
            // Force the popup to show the login screen first for unauthenticated users
            // (rather than the start screen). This IS honoured by Monerium today.
            "auth_mode" to "login",
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

        // Best-effort capture of the Monerium profile so future API calls don't have to
        // re-discover it. Failure here must not block onboarding — the user may legitimately
        // have no profile yet (KYB not started).
        val profile = fetchPreferredProfile(tokenResponse.accessToken)

        // If we got a profile id, reject the bind when another association already owns it.
        // Catches "I previously linked Monerium account X to association A; now I'm trying
        // to link X to association B". The guard does NOT fire when the profile fetch
        // failed or returned no profiles — accepting that gap is a deliberate trade of the
        // single-column design (the alternative would be to also persist tokenResponse.userId
        // for guarding purposes only).
        profile?.id?.let { profileId ->
            val existing = connectionRepo.findByMoneriumProfileId(profileId)
            if (existing != null && existing.association.id != oauthState.association.id) {
                logger.warn(
                    "Refusing to bind Monerium profile {} to association {} — already linked to {}",
                    profileId, oauthState.association.id, existing.association.id,
                )
                stateRepo.delete(oauthState)
                throw ConflictException("This Monerium account is already linked to another association")
            }
        }

        val walletAddress = profile?.id?.let { fetchPreferredAddress(tokenResponse.accessToken, it) }

        val connection = MoneriumConnection(
            association = oauthState.association,
            moneriumProfileId = profile?.id,
            moneriumProfileName = profile?.name,
            walletAddress = walletAddress,
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong()),
        )

        stateRepo.delete(oauthState)
        val saved = connectionRepo.save(connection)
        logger.info(
            "Monerium connection saved for association {} (profile={})",
            oauthState.association.id, profile?.id,
        )
        return saved
    }

    /**
     * Calls `GET /auth/context` with the freshly minted access token to find the profile
     * we should pin to this connection. `/auth/context` is reachable with just the `openid`
     * scope we request at consent time — `GET /profiles` would require the extra `profiles`
     * scope (and 403s without it).
     *
     * Prefers `defaultProfile` from the context; otherwise picks the first corporate profile
     * (expected case for associations); finally falls back to the first profile of any kind.
     *
     * Returns null on empty list or any HTTP failure — callers must treat null as "no
     * profile captured yet, will re-discover later" rather than as an error.
     */
    private fun fetchPreferredProfile(accessToken: String): MoneriumProfileDto? {
        return try {
            val headers = HttpHeaders().apply {
                setBearerAuth(accessToken)
                accept = listOf(MediaType.parseMediaType(MoneriumApiClient.API_ACCEPT))
            }
            val response = restTemplate.exchange(
                "${config.baseUrl}/auth/context",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                MoneriumAuthContextDto::class.java,
            )
            val body = response.body ?: return null
            val profiles = body.profiles
            val defaultProfile = body.defaultProfile?.let { id -> profiles.firstOrNull { it.id == id } }
            defaultProfile
                ?: profiles.firstOrNull { it.kind == MoneriumProfileKind.CORPORATE }
                ?: profiles.firstOrNull()
        } catch (ex: Exception) {
            logger.warn("Failed to fetch Monerium auth context during callback: {}", ex.message)
            null
        }
    }

    /**
     * Fetches the on-chain wallet address for the given profile from `GET /addresses`.
     *
     * Selects the address matching [OnchainConfig.moneriumChain]; falls back to the first
     * address of any chain. Returns null on empty list or any HTTP failure — callers must
     * treat null as "no wallet yet" and not block onboarding.
     */
    private fun fetchPreferredAddress(accessToken: String, profileId: String): String? = try {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
            accept = listOf(MediaType.parseMediaType(MoneriumApiClient.API_ACCEPT))
        }
        val response = restTemplate.exchange(
            "${config.baseUrl}/addresses?profile=$profileId",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            MoneriumAddressListDto::class.java,
        )
        val targetChain = onchainConfig.moneriumChain
        val addresses = response.body?.addresses.orEmpty()
        addresses.firstOrNull { it.chain == targetChain || it.chains?.contains(targetChain) == true }?.address
            ?: addresses.firstOrNull()?.address
    } catch (ex: Exception) {
        logger.warn("Failed to fetch Monerium addresses during callback: {}", ex.message)
        null
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
        val connection = connectionRepo.findByAssociation(association)
        val connected = connection != null
        val pending = !connected && stateRepo.existsByAssociationAndExpiresAtAfter(association, Instant.now())
        return MoneriumStatusDto(
            connected = connected,
            pending = pending,
            profileId = connection?.moneriumProfileId,
            profileName = connection?.moneriumProfileName,
        )
    }

    /**
     * Returns a Monerium access token that is guaranteed valid for at least
     * [REFRESH_SAFETY_MARGIN_SECONDS] seconds (proactive refresh).
     *
     * Use [forceRefreshAccessToken] when Monerium has rejected a structurally-fresh token
     * (Monerium-side revocation), as proactive refresh would return the same stale value.
     *
     * The refresh flow runs inside a pessimistic-locked transaction so that two concurrent
     * callers don't both refresh and invalidate each other's resulting refresh token.
     *
     * @throws MoneriumReauthRequiredException if the connection is BROKEN or the refresh call
     * itself returns `invalid_grant` (in which case the connection is flipped to BROKEN here).
     */
    @Transactional
    fun getValidAccessToken(associationId: UUID): String =
        withLockedConnection(associationId) { connection ->
            val safeUntil = Instant.now().plusSeconds(REFRESH_SAFETY_MARGIN_SECONDS)
            if (connection.expiresAt.isAfter(safeUntil)) connection.accessToken
            else refreshTokens(connection).accessToken
        }

    /**
     * Forces a refresh regardless of the stored expiry, returning the new access token.
     *
     * Called after Monerium 401s a structurally-fresh token (server-side revocation), where
     * [getValidAccessToken] would otherwise short-circuit and return the dead value.
     */
    @Transactional
    fun forceRefreshAccessToken(associationId: UUID): String =
        withLockedConnection(associationId) { connection ->
            refreshTokens(connection).accessToken
        }

    private inline fun <T> withLockedConnection(
        associationId: UUID,
        block: (MoneriumConnection) -> T,
    ): T {
        val connection = connectionRepo.findByAssociationIdForUpdate(associationId)
            ?: throw MoneriumReauthRequiredException("No Monerium connection for association $associationId")
        if (connection.state == MoneriumConnectionState.BROKEN) {
            throw MoneriumReauthRequiredException()
        }
        return block(connection)
    }

    /**
     * POSTs `grant_type=refresh_token` to Monerium and persists the new access/refresh pair.
     *
     * Monerium rotates the refresh token on each call; failing to persist the new value
     * silently breaks the connection on the next refresh. On `invalid_grant` the connection
     * is flagged BROKEN and [MoneriumReauthRequiredException] is thrown so the frontend can
     * re-trigger the PKCE flow.
     *
     * Must be called within a transaction that already holds the pessimistic lock on the
     * connection row (see [MoneriumConnectionRepository.findByAssociationIdForUpdate]).
     */
    private fun refreshTokens(connection: MoneriumConnection): MoneriumConnection {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "refresh_token")
            add("client_id", config.clientId)
            add("refresh_token", connection.refreshToken)
        }

        val tokenResponse = try {
            restTemplate.postForEntity(
                "${config.baseUrl}/auth/token",
                HttpEntity(body, headers),
                TokenResponse::class.java,
            ).body ?: throw IllegalStateException("Empty refresh response from Monerium")
        } catch (ex: HttpStatusCodeException) {
            logger.warn(
                "Monerium refresh rejected for association {}: status={} body={}",
                connection.association.id, ex.statusCode, ex.responseBodyAsString,
            )
            connection.state = MoneriumConnectionState.BROKEN
            connectionRepo.save(connection)
            throw MoneriumReauthRequiredException()
        }

        connection.accessToken = tokenResponse.accessToken
        connection.refreshToken = tokenResponse.refreshToken
        connection.expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong())
        val saved = connectionRepo.save(connection)
        logger.info("Refreshed Monerium tokens for association {}", connection.association.id)
        return saved
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

    companion object {
        /** Refresh proactively when the access token has under 30 s of life left. */
        const val REFRESH_SAFETY_MARGIN_SECONDS = 30L
    }
}

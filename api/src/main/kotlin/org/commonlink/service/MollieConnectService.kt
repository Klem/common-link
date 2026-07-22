package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonProperty
import org.commonlink.config.MollieConnectConfig
import org.commonlink.dto.MollieKycStatusDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOAuthState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.entity.User
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieOAuthStateRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.time.Instant
import java.util.Base64
import java.util.UUID

// --- Private Mollie API response DTOs (file-private, not part of the public API contract) ---

private data class TokenResponse(
    @field:JsonProperty("access_token") val accessToken: String,
    @field:JsonProperty("refresh_token") val refreshToken: String,
    @field:JsonProperty("expires_in") val expiresIn: Int,
)

private data class ClientLinkResponse(
    @field:JsonProperty("_links") val links: ClientLinkLinks?,
) {
    data class ClientLinkLinks(@field:JsonProperty("clientLink") val clientLink: Href?)
    data class Href(val href: String)
}

private data class OrganizationResponse(
    @field:JsonProperty("id") val id: String?,
)

private data class OnboardingResponse(
    @field:JsonProperty("status") val status: String,
    @field:JsonProperty("canReceivePayments") val canReceivePayments: Boolean,
    @field:JsonProperty("canReceiveSettlements") val canReceiveSettlements: Boolean,
)

/**
 * Handles @Transactional token operations with a pessimistic write lock.
 *
 * Extracted from [MollieConnectService] to avoid the Spring self-invocation proxy bypass:
 * [MollieConnectService.refreshOnboardingStatusIfStale] calls [getValidAccessToken] from
 * within the same service bean, which would silently skip @Transactional if not delegated
 * to a separately-proxied bean.
 */
@Component
class MollieConnectTokenManager(
    private val connectionRepo: MollieConnectionRepository,
    private val restTemplate: RestTemplate,
    private val config: MollieConnectConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns a valid Mollie access token, refreshing proactively if the token expires within
     * [REFRESH_SAFETY_MARGIN_SECONDS]. Holds a pessimistic write lock to prevent concurrent
     * double-refresh (two callers using the same refresh token simultaneously causes invalid_grant).
     *
     * @throws IllegalStateException if no connection exists or the connection is BROKEN.
     */
    @Transactional
    fun getValidAccessToken(associationId: UUID): String {
        val connection = connectionRepo.findByAssociationIdForUpdate(associationId)
            ?: throw IllegalStateException("No Mollie connection for association $associationId")
        if (connection.state == MollieConnectionState.BROKEN)
            throw IllegalStateException("Mollie connection is BROKEN for association $associationId")
        val safeUntil = Instant.now().plusSeconds(REFRESH_SAFETY_MARGIN_SECONDS)
        return if (connection.expiresAt.isAfter(safeUntil)) {
            connection.accessToken
        } else {
            refreshTokens(connection)
        }
    }

    /**
     * Forces a token refresh regardless of stored expiry.
     * Called after Mollie rejects a structurally-fresh token (server-side revocation).
     *
     * @throws IllegalStateException if no connection exists or the connection is BROKEN.
     */
    @Transactional
    fun forceRefreshAccessToken(associationId: UUID): String {
        val connection = connectionRepo.findByAssociationIdForUpdate(associationId)
            ?: throw IllegalStateException("No Mollie connection for association $associationId")
        if (connection.state == MollieConnectionState.BROKEN)
            throw IllegalStateException("Mollie connection is BROKEN for association $associationId")
        return refreshTokens(connection)
    }

    /**
     * POSTs grant_type=refresh_token to Mollie, persists the new token pair, and returns the
     * new access token. On any HTTP error (including invalid_grant), marks the connection
     * BROKEN and throws. Must be called within a transaction holding the pessimistic write lock.
     *
     * Tokens are never logged.
     */
    private fun refreshTokens(connection: MollieConnection): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            val encoded = Base64.getEncoder()
                .encodeToString("${config.clientId}:${config.clientSecret}".toByteArray())
            set("Authorization", "Basic $encoded")
        }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "refresh_token")
            add("refresh_token", connection.refreshToken)
        }

        val tokenResponse = try {
            restTemplate.postForEntity(
                "https://api.mollie.com/oauth2/tokens",
                HttpEntity(body, headers),
                TokenResponse::class.java,
            ).body ?: throw IllegalStateException("Empty refresh response from Mollie")
        } catch (ex: HttpStatusCodeException) {
            logger.warn(
                "Mollie token refresh rejected for association {}: status={}",
                connection.association.id, ex.statusCode,
            )
            connection.state = MollieConnectionState.BROKEN
            connectionRepo.save(connection)
            throw IllegalStateException("Mollie refresh token rejected — connection marked BROKEN")
        }

        connection.accessToken = tokenResponse.accessToken
        connection.refreshToken = tokenResponse.refreshToken
        connection.expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong())
        connectionRepo.save(connection)
        logger.info("Refreshed Mollie tokens for association {}", connection.association.id)
        return tokenResponse.accessToken
    }

    companion object {
        private const val REFRESH_SAFETY_MARGIN_SECONDS = 60L
    }
}

/**
 * Business logic for the Mollie Connect OAuth2 KYC onboarding flow.
 *
 * Flow: buildAuthorizationUrl → popup opens Client Link URL → Mollie redirects back →
 * handleCallback → redirect to success page → postMessage → card refreshes via getConnectionStatus.
 *
 * Token refresh is delegated to [MollieConnectTokenManager] (a separate Spring bean) so that
 * @Transactional + pessimistic write lock are properly intercepted even when called from
 * private internal methods (avoids Spring self-invocation proxy bypass).
 */
@Service
class MollieConnectService(
    private val config: MollieConnectConfig,
    private val connectionRepo: MollieConnectionRepository,
    private val stateRepo: MollieOAuthStateRepository,
    private val associationRepo: AssociationProfileRepository,
    private val restTemplate: RestTemplate,
    private val tokenManager: MollieConnectTokenManager,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Generates the Mollie authorization URL for the given user's association.
     *
     * Mock mode ([MollieConnectConfig.mock] = true): creates a COMPLETED connection locally
     * and returns the success page URL — no Mollie API call made.
     * Normal mode: creates a Client Link via POST /v2/client-links (organization access token),
     * persists a CSRF state record, and appends OAuth2 query params to the client-link URL.
     *
     * @param userId UUID of the authenticating user.
     * @return URL to open in the frontend popup.
     * @throws NotFoundException if the user has no association profile.
     * @throws IllegalStateException if the Mollie client-link call fails.
     */
    fun buildAuthorizationUrl(userId: UUID): String {
        val association = associationRepo.findByUserId(userId)
            .orElseThrow { NotFoundException("Association not found for user: $userId") }

        if (config.mock) {
            return buildMockConnection(association)
        }

        stateRepo.deleteByAssociationId(association.id!!)
        val state = UUID.randomUUID().toString()
        stateRepo.save(
            MollieOAuthState(
                state = state,
                association = association,
                expiresAt = Instant.now().plusSeconds(600),
            )
        )

        val clientLinkHref = createClientLink(association, association.user)

        // redirect_uri is NOT a client-link query param (doc verified) — the registered URI governs.
        val authUrl = buildString {
            append(clientLinkHref)
            append("?client_id=").append(URLEncoder.encode(config.clientId, "UTF-8"))
            append("&state=").append(URLEncoder.encode(state, "UTF-8"))
            append("&scope=").append(URLEncoder.encode(config.scopes, "UTF-8"))
            append("&approval_prompt=force")
        }

        logger.info("Generated Mollie Connect auth URL for association {}", association.id)
        return authUrl
    }

    private fun buildMockConnection(association: AssociationProfile): String {
        val existing = connectionRepo.findByAssociationId(association.id!!)
        val mockConn = existing ?: MollieConnection(
            association = association,
            accessToken = "mock",
            refreshToken = "mock",
            expiresAt = Instant.now().plusSeconds(3600),
        )
        mockConn.accessToken = "mock"
        mockConn.refreshToken = "mock"
        mockConn.expiresAt = Instant.now().plusSeconds(3600)
        mockConn.state = MollieConnectionState.ACTIVE
        mockConn.onboardingStatus = MollieOnboardingStatus.COMPLETED
        mockConn.canReceivePayments = true
        mockConn.canReceiveSettlements = true
        mockConn.mollieOrganizationId = "org_mock_${association.id}"
        mockConn.lastSyncedAt = Instant.now()
        connectionRepo.save(mockConn)
        logger.info("Mock Mollie Connect: COMPLETED connection for association {}", association.id)
        return "$frontendUrl/en/mollie-connect/success"
    }

    /**
     * Calls POST /v2/client-links with the association's data and returns the client-link href.
     *
     * Auth: organization access token (clients.write permission) — NOT the payment api-key.
     * Payload structure (verified against Mollie OpenAPI spec):
     *   - name + address at root level (organization details)
     *   - owner = physical contact person (email / givenName / familyName / locale)
     *
     * ⚠ Country defaults to "FR" — all CommonLink associations are French. A dedicated country
     *   field on AssociationProfile would be cleaner; tracked in .tasks/todo.md.
     * ⚠ givenName/familyName split on first space from contactName — best-effort approximation.
     */
    private fun createClientLink(association: AssociationProfile, user: User): String {
        val contactName = association.contactName ?: user.displayName ?: user.email
        val givenName = contactName.substringBefore(" ")
        val familyName = contactName.substringAfter(" ", "")

        val addressMap = mutableMapOf<String, Any?>("country" to "FR").apply {
            association.addressLine1?.let { put("streetAndNumber", it) }
            association.postalCode?.let { put("postalCode", it) }
            association.city?.let { put("city", it) }
        }

        val requestBody = mutableMapOf<String, Any>(
            "name" to association.name,
            "address" to addressMap,
            "owner" to mapOf(
                "email" to user.email,
                "givenName" to givenName,
                "familyName" to familyName,
                "locale" to "fr_FR",
            ),
        ).apply {
            association.siren?.let { put("registrationNumber", it) }
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(config.organizationToken)
        }

        val response = try {
            restTemplate.postForEntity(
                "https://api.mollie.com/v2/client-links",
                HttpEntity(requestBody, headers),
                ClientLinkResponse::class.java,
            )
        } catch (ex: HttpStatusCodeException) {
            logger.warn(
                "Mollie client-links call failed for association {}: status={}",
                association.id, ex.statusCode,
            )
            throw IllegalStateException("Failed to create Mollie client link: ${ex.statusCode}")
        }

        return response.body?.links?.clientLink?.href
            ?: throw IllegalStateException("Mollie client-links response missing _links.clientLink.href")
    }

    /**
     * Processes the Mollie OAuth2 callback after user authorization.
     *
     * Transactional decomposition (jpa-rules: no HTTP inside a DB transaction):
     * - Phase 1: DB reads — validate CSRF state, extract associationId
     * - Phase 2: HTTP — token exchange, org fetch, onboarding status fetch (no transaction open)
     * - Phase 3: DB writes — uniqueness guard, persist connection, delete consumed state
     *
     * @throws IllegalStateException on invalid/expired state, Mollie error, or org already linked.
     */
    fun handleCallback(code: String, state: String) {
        // Phase 1 — validate state
        val oauthState = stateRepo.findById(state).orElseThrow {
            logger.warn("Unknown Mollie OAuth state: {}", state)
            IllegalStateException("Invalid state")
        }
        if (oauthState.expiresAt.isBefore(Instant.now())) {
            stateRepo.deleteById(state)
            throw IllegalStateException("State expired")
        }
        val associationId = oauthState.association.id!!

        // Phase 2 — HTTP calls (no transaction open)
        val tokenResponse = exchangeCode(code)
        val organizationId = fetchOrganizationId(tokenResponse.accessToken)
        val onboardingResponse = fetchOnboardingStatus(tokenResponse.accessToken)

        // Phase 3 — persist
        val existingForOrg = connectionRepo.findByMollieOrganizationId(organizationId)
        if (existingForOrg != null && existingForOrg.association.id != associationId) {
            logger.warn(
                "Refusing Mollie org {} link to association {} — already bound to {}",
                organizationId, associationId, existingForOrg.association.id,
            )
            throw IllegalStateException("Mollie organization already linked to another association")
        }

        val association = associationRepo.findById(associationId)
            .orElseThrow { IllegalStateException("Association $associationId not found during callback") }
        val connection = connectionRepo.findByAssociationId(associationId)
            ?: MollieConnection(
                association = association,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong()),
            )

        connection.accessToken = tokenResponse.accessToken
        connection.refreshToken = tokenResponse.refreshToken
        connection.expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong())
        connection.state = MollieConnectionState.ACTIVE
        connection.onboardingStatus = MollieOnboardingStatus.fromMollie(onboardingResponse.status)
        connection.canReceivePayments = onboardingResponse.canReceivePayments
        connection.canReceiveSettlements = onboardingResponse.canReceiveSettlements
        connection.mollieOrganizationId = organizationId
        connection.lastSyncedAt = Instant.now()
        connectionRepo.save(connection)

        stateRepo.deleteById(state)
        logger.info(
            "Mollie Connect callback successful for association {}, status={}",
            associationId, onboardingResponse.status,
        )
    }

    /**
     * Returns a valid Mollie access token for the given association (proactive refresh if near expiry).
     * Delegates to [MollieConnectTokenManager] for proper @Transactional + pessimistic lock.
     */
    fun getValidAccessToken(associationId: UUID): String =
        tokenManager.getValidAccessToken(associationId)

    /**
     * Forces a token refresh regardless of stored expiry. Use when Mollie rejects a token
     * that has not yet expired locally (server-side revocation).
     * Delegates to [MollieConnectTokenManager].
     */
    fun forceRefreshAccessToken(associationId: UUID): String =
        tokenManager.forceRefreshAccessToken(associationId)

    /**
     * Returns the Mollie KYC status for the given user's association. If a non-COMPLETED
     * connection is stale (last sync > 5 minutes ago), triggers a throttled re-fetch from Mollie
     * before returning — this is how in-review → completed transitions surface without webhooks
     * (Mollie has no onboarding webhook; see sprint decision 6).
     *
     * @param userId UUID of the authenticating user.
     */
    fun getConnectionStatus(userId: UUID): MollieKycStatusDto {
        val association = associationRepo.findByUserId(userId)
            .orElseThrow { NotFoundException("Association not found for user: $userId") }
        val associationId = association.id!!
        val connection = connectionRepo.findByAssociationId(associationId)

        if (connection != null) {
            refreshOnboardingStatusIfStale(connection)
        }

        val pending = stateRepo.existsByAssociationIdAndExpiresAtAfter(associationId, Instant.now())

        return MollieKycStatusDto(
            connected = connection != null,
            pending = pending,
            broken = connection?.state == MollieConnectionState.BROKEN,
            onboardingStatus = connection?.onboardingStatus?.name,
            canReceivePayments = connection?.canReceivePayments,
        )
    }

    /**
     * Re-fetches the onboarding status from Mollie if the connection is non-COMPLETED and
     * the last sync is older than 5 minutes. All failures are caught and logged as WARN —
     * the caller always receives the last-known DB state, never an error from this method.
     *
     * Uses [tokenManager] (a separate bean) so the @Transactional pessimistic lock on
     * [getValidAccessToken] is properly applied — avoids Spring self-invocation proxy bypass.
     */
    private fun refreshOnboardingStatusIfStale(connection: MollieConnection) {
        if (connection.state == MollieConnectionState.BROKEN) return
        if (connection.onboardingStatus == MollieOnboardingStatus.COMPLETED) return
        val fiveMinutesAgo = Instant.now().minusSeconds(300)
        if (connection.lastSyncedAt?.isAfter(fiveMinutesAgo) == true) return

        val associationId = connection.association.id!!
        try {
            val token = tokenManager.getValidAccessToken(associationId)
            val onboarding = fetchOnboardingStatus(token)
            // Re-fetch to avoid overwriting token updates that tokenManager committed above
            val updated = connectionRepo.findByAssociationId(associationId) ?: return
            updated.onboardingStatus = MollieOnboardingStatus.fromMollie(onboarding.status)
            updated.canReceivePayments = onboarding.canReceivePayments
            updated.canReceiveSettlements = onboarding.canReceiveSettlements
            updated.lastSyncedAt = Instant.now()
            connectionRepo.save(updated)
            logger.info(
                "Refreshed Mollie onboarding status for association {}: {}",
                associationId, onboarding.status,
            )
        } catch (ex: Exception) {
            logger.warn(
                "Failed to refresh Mollie onboarding status for association {}: {}",
                associationId, ex.message,
            )
        }
    }

    private fun exchangeCode(code: String): TokenResponse {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            val encoded = Base64.getEncoder()
                .encodeToString("${config.clientId}:${config.clientSecret}".toByteArray())
            set("Authorization", "Basic $encoded")
        }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("code", code)
            add("redirect_uri", config.redirectUri)
        }
        val response = restTemplate.postForEntity(
            "https://api.mollie.com/oauth2/tokens",
            HttpEntity(body, headers),
            TokenResponse::class.java,
        )
        return response.body ?: throw IllegalStateException("Empty token response from Mollie")
    }

    private fun fetchOrganizationId(accessToken: String): String {
        val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
        val response = restTemplate.exchange(
            "https://api.mollie.com/v2/organizations/me",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            OrganizationResponse::class.java,
        )
        return response.body?.id
            ?: throw IllegalStateException("Mollie organizations/me returned no organization id")
    }

    private fun fetchOnboardingStatus(accessToken: String): OnboardingResponse {
        val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
        val response = restTemplate.exchange(
            "https://api.mollie.com/v2/onboarding/me",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            OnboardingResponse::class.java,
        )
        return response.body
            ?: throw IllegalStateException("Mollie onboarding/me returned empty response")
    }
}

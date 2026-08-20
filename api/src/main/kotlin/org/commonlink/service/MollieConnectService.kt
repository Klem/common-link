package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.commonlink.config.MollieConnectConfig
import org.commonlink.config.MollieProperties
import org.commonlink.config.OnboardingApi
import org.commonlink.dto.MollieKycStatusDto
import org.commonlink.event.MollieOnboardingStatusChangedEvent
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOAuthState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.entity.User
import org.commonlink.exception.MollieRefreshRejectedException
import org.commonlink.exception.MollieRefreshUnavailableException
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
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import java.net.URLEncoder
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Token value written by [MollieConnectService.buildMockConnection] when
 * `app.mollie.connect.mock=true`. Never a real Mollie credential: it exists only so the
 * onboarding UI can be walked through without an OAuth popup. Payment paths must fail fast
 * rather than present it to Mollie — see [MollieConnectTokenManager.refreshTokens].
 */
internal const val MOCK_TOKEN_SENTINEL = "mock"

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

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CapabilitiesResponse(
    @field:JsonProperty("_embedded") val embedded: Embedded? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Embedded(val capabilities: List<Capability>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Capability(
        val name: String,
        val status: String,
        val statusReason: String? = null,
        val requirements: List<Requirement>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Requirement(
        val id: String,
        val status: String,
        @field:JsonProperty("_links") val links: ReqLinks? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ReqLinks(val dashboard: Href? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Href(val href: String)
}

private data class OnboardingSnapshot(
    val onboardingStatus: MollieOnboardingStatus,
    val canReceivePayments: Boolean,
    val canReceiveSettlements: Boolean,
    val dashboardUrl: String?,
)

/**
 * Maps a Capabilities API response to the 4-field snapshot used internally.
 *
 * Status derivation:
 * - payments capability `status=enabled` → COMPLETED
 * - payments capability absent → NEEDS_DATA (account not yet set up)
 * - payments capability present + requirement currently-due/past-due or statusReason signals info needed → NEEDS_DATA
 * - payments capability pending with no immediate action required → IN_REVIEW
 *
 * Dashboard URL: first currently-due/past-due requirement with a dashboard link; falls back to
 * any requirement with a dashboard link (e.g. if none are currently-due yet).
 */
private fun CapabilitiesResponse.toOnboardingSnapshot(): OnboardingSnapshot {
    val caps = embedded?.capabilities ?: emptyList()
    val payments = caps.firstOrNull { it.name == "payments" }
    val settlements = caps.firstOrNull { it.name == "settlements" }

    val canReceivePayments = payments?.status == "enabled"
    val canReceiveSettlements = settlements?.status == "enabled"

    val onboardingStatus = when {
        canReceivePayments -> MollieOnboardingStatus.COMPLETED
        payments == null -> MollieOnboardingStatus.NEEDS_DATA
        else -> {
            val actionRequired = payments.requirements
                ?.any { it.status == "currently-due" || it.status == "past-due" } == true
                || payments.statusReason == "onboarding-information-needed"
                || payments.statusReason == "requirement-past-due"
            if (actionRequired) MollieOnboardingStatus.NEEDS_DATA else MollieOnboardingStatus.IN_REVIEW
        }
    }

    val dashboardUrl = payments?.requirements
        ?.filter { it.status == "currently-due" || it.status == "past-due" }
        ?.firstNotNullOfOrNull { it.links?.dashboard?.href }
        ?: payments?.requirements?.firstNotNullOfOrNull { it.links?.dashboard?.href }

    return OnboardingSnapshot(onboardingStatus, canReceivePayments, canReceiveSettlements, dashboardUrl)
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OnboardingMeResponse(
    val status: String,
    val canReceivePayments: Boolean = false,
    val canReceiveSettlements: Boolean = false,
    @field:JsonProperty("_links") val links: OnboardingMeLinks? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OnboardingMeLinks(val dashboard: Href? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Href(val href: String)
}

/** Maps a legacy `/v2/onboarding/me` response to the 4-field snapshot used internally. */
private fun OnboardingMeResponse.toOnboardingSnapshot(): OnboardingSnapshot {
    val onboardingStatus = when (status) {
        "completed"  -> MollieOnboardingStatus.COMPLETED
        "in-review"  -> MollieOnboardingStatus.IN_REVIEW
        else         -> MollieOnboardingStatus.NEEDS_DATA
    }
    return OnboardingSnapshot(
        onboardingStatus = onboardingStatus,
        canReceivePayments = canReceivePayments,
        canReceiveSettlements = canReceiveSettlements,
        dashboardUrl = links?.dashboard?.href,
    )
}

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
    private val mollieProperties: MollieProperties,
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
    fun getValidAccessToken(associationId: UUID): String =
        getValidAccessToken(associationId, REFRESH_SAFETY_MARGIN_SECONDS)

    /**
     * Same as [getValidAccessToken] but with an explicit refresh lookahead, so the scheduled
     * refresh ([MollieTokenRefreshExecutor]) can renew tokens long before a donor needs them
     * while reusing this exact locked path.
     *
     * Reusing it — rather than calling [forceRefreshAccessToken] — is what makes the scheduled
     * refresh idempotent across instances: the expiry re-check happens *after* the row lock is
     * acquired, so a second instance arriving on an already-renewed connection does no HTTP call.
     *
     * @param safetyMarginSeconds refresh when the token expires within this many seconds.
     * @throws IllegalStateException if no connection exists or the connection is BROKEN.
     */
    @Transactional
    fun getValidAccessToken(associationId: UUID, safetyMarginSeconds: Long): String {
        val connection = connectionRepo.findByAssociationIdForUpdate(associationId)
            ?: throw IllegalStateException("No Mollie connection for association $associationId")
        if (connection.state == MollieConnectionState.BROKEN)
            throw IllegalStateException("Mollie connection is BROKEN for association $associationId")
        val safeUntil = Instant.now().plusSeconds(safetyMarginSeconds)
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
     * new access token. Must be called within a transaction holding the pessimistic write lock.
     *
     * Failures are classified rather than lumped together, because the remedies are opposites:
     * [MollieRefreshRejectedException] (4xx — the grant is dead, only a re-OAuth recovers) versus
     * [MollieRefreshUnavailableException] (429/5xx/IO — retrying works). This method deliberately
     * does **not** persist [MollieConnectionState.BROKEN]: writing it here was dead code, since
     * throwing marks the surrounding transaction rollback-only and the row stayed ACTIVE anyway.
     * The decision belongs to the caller, which can commit it in its own transaction —
     * see [MollieTokenRefreshExecutor].
     *
     * Mock connections short-circuit: presenting [MOCK_TOKEN_SENTINEL] to Mollie can only ever
     * yield 400 invalid_grant, so it is refused locally instead of being sent.
     *
     * Tokens are never logged; Mollie's OAuth error body carries only an error code.
     */
    private fun refreshTokens(connection: MollieConnection): String {
        if (connection.refreshToken == MOCK_TOKEN_SENTINEL) {
            logger.warn(
                "Mollie connection for association {} is a mock (app.mollie.connect.mock=true) — " +
                    "its 1h token has lapsed and no real refresh is possible. Set " +
                    "MOLLIE_CONNECT_MOCK=false and redo the OAuth flow to collect payments.",
                connection.association.id,
            )
            throw IllegalStateException("Mollie connection is mocked — real payments are impossible")
        }
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
                "${mollieProperties.apiBaseUrl}/oauth2/tokens",
                HttpEntity(body, headers),
                TokenResponse::class.java,
            ).body ?: throw MollieRefreshUnavailableException(message = "Empty refresh response from Mollie")
        } catch (ex: HttpStatusCodeException) {
            val status = ex.statusCode
            // 429 is a 4xx but says nothing about the grant — never treat throttling as a dead grant.
            val definitive = status.is4xxClientError && status.value() != TOO_MANY_REQUESTS
            logger.warn(
                "Mollie token refresh failed for association {}: status={} definitive={} body={}",
                connection.association.id, status, definitive, ex.responseBodyAsString,
            )
            throw if (definitive) {
                MollieRefreshRejectedException(status, ex.responseBodyAsString)
            } else {
                MollieRefreshUnavailableException(status)
            }
        } catch (ex: RestClientException) {
            logger.warn(
                "Mollie token refresh unreachable for association {}: {}",
                connection.association.id, ex.message,
            )
            throw MollieRefreshUnavailableException(message = "Mollie /oauth2/tokens unreachable")
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
        private const val TOO_MANY_REQUESTS = 429
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
    private val onboardingGate: OnboardingGateService,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
    private val mollieProperties: MollieProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Generates the Mollie authorization URL for the given user's association.
     *
     * Mock mode ([MollieConnectConfig.mock] = true): creates a COMPLETED connection locally
     * and returns the success page URL — no Mollie API call made.
     * Normal mode: creates a Client Link via POST /v2/client-links (Bearer advanced access token),
     * persists a CSRF state record, and appends OAuth2 query params to the client-link URL.
     *
     * @param userId UUID of the authenticating user.
     * @return URL to open in the frontend popup.
     * @throws NotFoundException if the user has no association profile.
     * @throws org.commonlink.exception.ConflictException if no signed fiscal mandate exists yet.
     * @throws IllegalStateException if the Mollie client-link call fails.
     */
    fun buildAuthorizationUrl(userId: UUID): String {
        val association = associationRepo.findByUserId(userId)
            .orElseThrow { NotFoundException("Association not found for user: $userId") }

        // Chain guard (mirrors frontend tab lock): a bank account can only be connected once the
        // fiscal mandate is signed. Enforced even in mock mode so the flow order is replay-safe.
        onboardingGate.requireMandateSigned(userId)

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
            accessToken = MOCK_TOKEN_SENTINEL,
            refreshToken = MOCK_TOKEN_SENTINEL,
            expiresAt = Instant.now().plusSeconds(3600),
        )
        mockConn.accessToken = MOCK_TOKEN_SENTINEL
        mockConn.refreshToken = MOCK_TOKEN_SENTINEL
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
     * Auth: Bearer with the organization's Advanced access token ([MollieConnectConfig.advancedToken],
     * provisioned in the Mollie dashboard) carrying the clients.write permission. HTTP Basic
     * (client_id:client_secret) is valid ONLY on the /oauth2/tokens endpoints — Mollie rejects it
     * here with 400 {"detail":"Invalid Authorization header"}.
     * Body: application/json —
     *   - name + address + legalEntity at root level (organization details)
     *   - registrationNumber: only when [AssociationProfile.siren] is set — Mollie validates it
     *     against the chamber-of-commerce registry (SIREN in France) and the field is optional;
     *     the RNA identifier (W…) must never be sent there
     *   - address: country always; streetAndNumber/postalCode/city only when all three are set
     *     (Mollie requires postalCode + city as soon as a street is provided)
     *   - legalEntity: "fr-association" (Mollie legal-entity list) — all CommonLink associations
     *   - owner = physical contact person (email from [AssociationProfile.contactEmail] / givenName /
     *     familyName from [AssociationProfile.contactName] / locale fr_FR)
     *
     * Every field pre-filled here is one less field the association has to type in the Mollie
     * hosted onboarding wizard.
     *
     * ⚠ Country defaults to "FR" — all CommonLink associations are French. A dedicated country
     *   field on AssociationProfile would be cleaner; tracked in .tasks/todo.md.
     * ⚠ givenName/familyName split on first space from contactName — best-effort approximation.
     * ⚠ contactEmail and contactName must be set on the profile before calling this method; both throw [IllegalStateException] if absent.
     */
    private fun createClientLink(association: AssociationProfile, user: User): String {
        val contactEmail = association.contactEmail
            ?: throw IllegalStateException("Contact email is not set for association ${association.id}")
        val contactName = association.contactName
            ?: throw IllegalStateException("Contact name is not set for association ${association.id}")
        val spaceIdx = contactName.indexOf(' ')
        val givenName = if (spaceIdx > 0) contactName.substring(0, spaceIdx) else contactName
        val familyName = if (spaceIdx > 0) contactName.substring(spaceIdx + 1) else contactName

        val address = mutableMapOf<String, Any>("country" to "FR")
        val street = association.addressLine1
        val postalCode = association.postalCode
        val city = association.city
        if (street != null && postalCode != null && city != null) {
            address["streetAndNumber"] = street
            address["postalCode"] = postalCode
            address["city"] = city
        }

        val requestBody = mutableMapOf<String, Any>(
            "name" to association.name,
            "address" to address,
            "legalEntity" to "fr-association",
            "owner" to mapOf(
                "email" to contactEmail,
                "givenName" to givenName,
                "familyName" to familyName,
                "locale" to "fr_FR",
            ),
        ).apply {
            association.siren?.let { put("registrationNumber", it) }
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(config.advancedToken)
        }

        val response = try {
            restTemplate.postForEntity<ClientLinkResponse>(
                "${mollieProperties.apiBaseUrl}/v2/client-links",
                HttpEntity(requestBody, headers),
            )
        } catch (ex: HttpStatusCodeException) {
            logger.warn(
                "Mollie client-links call failed for association {}: requestBody={} status={} responseBody={}",
                association.id, requestBody, ex.statusCode, ex.responseBodyAsString,
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
     * LCB-FT boundary: this method writes exclusively to [MollieConnection] — access token,
     * refresh token, expiry, connection state, onboarding status, canReceivePayments,
     * canReceiveSettlements, onboardingDashboardUrl, mollieOrganizationId, lastSyncedAt.
     * No field of [org.commonlink.entity.AssociationProfile] is written here. Mollie's onboarding
     * validation is conducted by Mollie for its own regulatory purposes and does not constitute a
     * CommonLink due-diligence act; CommonLink's KYB dossier is validated independently by
     * [VerificationService]. The two validations are cumulative — neither substitutes for the other.
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
        val snapshot = fetchOnboardingSnapshot(tokenResponse.accessToken)

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
        connection.onboardingStatus = snapshot.onboardingStatus
        connection.canReceivePayments = snapshot.canReceivePayments
        connection.canReceiveSettlements = snapshot.canReceiveSettlements
        connection.onboardingDashboardUrl = snapshot.dashboardUrl
        connection.mollieOrganizationId = organizationId
        connection.lastSyncedAt = Instant.now()
        connectionRepo.save(connection)

        stateRepo.deleteById(state)
        logger.info(
            "Mollie Connect callback successful for association {}, status={}",
            associationId, snapshot.onboardingStatus,
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
     * connection is stale (last sync > 5 minutes ago), triggers a throttled re-fetch via the
     * configured onboarding API (see [org.commonlink.config.MollieConnectConfig.onboardingApi])
     * before returning — this is how transitions surface without webhooks (Mollie has no onboarding webhook). On a status change, a
     * [MollieOnboardingStatusChangedEvent] is published for async email delivery.
     *
     * @param userId UUID of the authenticating user.
     */
    fun getConnectionStatus(userId: UUID): MollieKycStatusDto {
        val association = associationRepo.findByUserId(userId)
            .orElseThrow { NotFoundException("Association not found for user: $userId") }
        val associationId = association.id!!
        val stored = connectionRepo.findByAssociationId(associationId)
        // Use the refreshed instance when a sync happened, so the DTO reflects it immediately
        val connection = stored?.let { refreshOnboardingStatusIfStale(it) ?: it }

        val pending = stateRepo.existsByAssociationIdAndExpiresAtAfter(associationId, Instant.now())

        return MollieKycStatusDto(
            connected = connection != null,
            pending = pending,
            broken = connection?.state == MollieConnectionState.BROKEN,
            onboardingStatus = connection?.onboardingStatus?.name,
            canReceivePayments = connection?.canReceivePayments,
            dashboardUrl = connection?.onboardingDashboardUrl,
            canForceComplete = config.allowFakeCompletion,
        )
    }

    /**
     * DEV/STAGING ONLY — simulates Mollie validating the association's KYC.
     *
     * Flips an EXISTING real connection to COMPLETED with canReceivePayments/canReceiveSettlements
     * true, exactly as [refreshOnboardingStatusIfStale] would after the configured onboarding API
     * reports the KYC as validated. The real OAuth popup + client-link creation are
     * left untouched — this only fakes the final validation step Mollie has no dashboard button for.
     *
     * No runtime flag guard here: access is gated declaratively by [org.commonlink.controller.MollieConnectMockController],
     * whose bean only exists when `app.mollie.connect.allow-fake-completion=true` (mirrors the
     * on-chain [org.commonlink.onchain.MockOnchainRegistry] style). Base `application.yml` defaults
     * the flag to `true` for local dev; `application-prod.yml` overrides it to `false` explicitly
     * (and the controller carries `@Profile("!prod")`), so under the prod profile the controller is
     * absent and this method is unreachable. Prod safety depends on that explicit override, not the
     * base default — see the C1 finding in the 2026-07-24 security audit.
     *
     * Requires a connection to already exist (we never fabricate one here — that is
     * [buildMockConnection]'s job). Once COMPLETED, [refreshOnboardingStatusIfStale] short-circuits
     * and never re-polls Mollie, so the forced state is durable.
     *
     * @param userId UUID of the authenticating user.
     * @throws IllegalStateException when no connection exists.
     */
    @Transactional
    fun forceCompleteOnboarding(userId: UUID): MollieKycStatusDto {
        val association = associationRepo.findByUserId(userId)
            .orElseThrow { NotFoundException("Association not found for user: $userId") }
        val connection = connectionRepo.findByAssociationId(association.id!!)
            ?: throw IllegalStateException("No Mollie connection to complete — connect first")

        connection.state = MollieConnectionState.ACTIVE
        connection.onboardingStatus = MollieOnboardingStatus.COMPLETED
        connection.canReceivePayments = true
        connection.canReceiveSettlements = true
        connection.lastSyncedAt = Instant.now()
        connectionRepo.save(connection)
        logger.warn(
            "DEV: forced Mollie onboarding COMPLETED for association {} (allowFakeCompletion=true)",
            association.id,
        )
        return getConnectionStatus(userId)
    }

    /**
     * Re-fetches the onboarding status from Mollie if the connection is non-COMPLETED and
     * the last sync is older than 5 minutes. All failures are caught and logged as WARN —
     * the caller always receives the last-known DB state, never an error from this method.
     *
     * Uses [tokenManager] (a separate bean) so the @Transactional pessimistic lock on
     * [getValidAccessToken] is properly applied — avoids Spring self-invocation proxy bypass.
     *
     * @return the freshly-synced connection instance, or null when the sync was skipped
     *         (BROKEN / COMPLETED / throttled) or failed — callers fall back to their instance.
     */
    private fun refreshOnboardingStatusIfStale(connection: MollieConnection): MollieConnection? {
        if (connection.state == MollieConnectionState.BROKEN) return null
        if (connection.onboardingStatus == MollieOnboardingStatus.COMPLETED) return null
        val fiveMinutesAgo = Instant.now().minusSeconds(300)
        if (connection.lastSyncedAt?.isAfter(fiveMinutesAgo) == true) return null

        val associationId = connection.association.id!!
        val previousStatus = connection.onboardingStatus
        return try {
            val token = tokenManager.getValidAccessToken(associationId)
            val snapshot = fetchOnboardingSnapshot(token)
            // Re-fetch to avoid overwriting token updates that tokenManager committed above
            val updated = connectionRepo.findByAssociationId(associationId) ?: return null
            updated.onboardingStatus = snapshot.onboardingStatus
            updated.canReceivePayments = snapshot.canReceivePayments
            updated.canReceiveSettlements = snapshot.canReceiveSettlements
            updated.onboardingDashboardUrl = snapshot.dashboardUrl
            updated.lastSyncedAt = Instant.now()
            connectionRepo.save(updated)
            logger.info(
                "Refreshed Mollie onboarding status for association {}: {}",
                associationId, updated.onboardingStatus,
            )
            if (updated.onboardingStatus != previousStatus) {
                eventPublisher.publishEvent(
                    MollieOnboardingStatusChangedEvent(
                        associationId = associationId,
                        previousStatus = previousStatus,
                        newStatus = updated.onboardingStatus,
                    )
                )
            }
            updated
        } catch (ex: Exception) {
            logger.warn(
                "Failed to refresh Mollie onboarding status for association {} via {} API: {}",
                associationId, config.onboardingApi, ex.message,
            )
            null
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
            "${mollieProperties.apiBaseUrl}/oauth2/tokens",
            HttpEntity(body, headers),
            TokenResponse::class.java,
        )
        return response.body ?: throw IllegalStateException("Empty token response from Mollie")
    }

    private fun fetchOrganizationId(accessToken: String): String {
        val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
        val response = restTemplate.exchange(
            "${mollieProperties.apiBaseUrl}/v2/organizations/me",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            OrganizationResponse::class.java,
        )
        return response.body?.id
            ?: throw IllegalStateException("Mollie organizations/me returned no organization id")
    }

    private fun fetchOnboardingSnapshot(accessToken: String): OnboardingSnapshot = when (config.onboardingApi) {
        OnboardingApi.LEGACY        -> fetchOnboardingMe(accessToken).toOnboardingSnapshot()
        OnboardingApi.CAPABILITIES  -> fetchCapabilities(accessToken).toOnboardingSnapshot()
    }

    private fun fetchOnboardingMe(accessToken: String): OnboardingMeResponse {
        val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
        val response = restTemplate.exchange(
            "${mollieProperties.apiBaseUrl}/v2/onboarding/me",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            OnboardingMeResponse::class.java,
        )
        return response.body
            ?: throw IllegalStateException("Mollie onboarding/me returned empty response")
    }

    private fun fetchCapabilities(accessToken: String): CapabilitiesResponse {
        val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
        val response = restTemplate.exchange(
            "${mollieProperties.apiBaseUrl}/v2/capabilities",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            CapabilitiesResponse::class.java,
        )
        return response.body
            ?: throw IllegalStateException("Mollie capabilities returned empty response")
    }
}

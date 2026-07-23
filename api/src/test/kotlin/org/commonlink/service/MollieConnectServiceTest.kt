package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOAuthState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieOAuthStateRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for [MollieConnectService] and [MollieConnectTokenManager].
 *
 * Uses [MockRestServiceServer] bound to the shared [RestTemplate] bean so that all
 * outbound HTTP calls to api.mollie.com are intercepted and answered with JSON stubs —
 * no real network calls are made. The private Mollie response DTOs (TokenResponse, etc.)
 * are transparently deserialized by the RestTemplate's own Jackson converters from the
 * JSON stubs, avoiding the need to reference file-private classes in tests.
 *
 * Each test runs inside the test-managed @Transactional boundary that rolls back after
 * the method, keeping tests isolated. The mock server is reset in @BeforeEach and
 * verified in @AfterEach to confirm all expected HTTP calls were actually made.
 */
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
    "app.mollie.connect.client-id=test_client",
    "app.mollie.connect.client-secret=test_secret",
    "app.mollie.connect.advanced-token=test_advanced_token",
    "app.mollie.connect.redirect-uri=http://localhost:8080/api/public/webhooks/mollie-connect",
    "app.mollie.connect.scopes=onboarding.read",
    "app.mollie.connect.mock=false",
])
@Transactional
class MollieConnectServiceTest {

    @Autowired private lateinit var mollieConnectService: MollieConnectService
    @Autowired private lateinit var tokenManager: MollieConnectTokenManager
    @Autowired private lateinit var restTemplate: RestTemplate
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var mollieConnectionRepository: MollieConnectionRepository
    @Autowired private lateinit var mollieOAuthStateRepository: MollieOAuthStateRepository

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var association: AssociationProfile
    private lateinit var userId: UUID
    private lateinit var associationId: UUID

    @BeforeEach
    fun setUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate)
        val user = userRepository.save(
            TestFixtures.associationUser(email = "mollie-${System.nanoTime()}@example.com")
        )
        association = associationProfileRepository.save(TestFixtures.associationProfile(user))
        userId = user.id!!
        associationId = association.id!!
    }

    @AfterEach
    fun tearDown() {
        mockServer.verify()
    }

    // ── buildAuthorizationUrl ─────────────────────────────────────────────────

    @Test
    fun `buildAuthorizationUrl - throws NotFoundException when association not found`() {
        assertThrows<NotFoundException> {
            mollieConnectService.buildAuthorizationUrl(UUID.randomUUID())
        }
    }

    @Test
    fun `buildAuthorizationUrl - calls client-links with bearer advanced token and returns URL with state`() {
        mockServer.expect(requestTo("https://api.mollie.com/v2/client-links"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test_advanced_token"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            // Required fields only — optional fields are dropped to avoid Mollie field-level 400s.
            .andExpect(jsonPath("$.name").exists())
            .andExpect(jsonPath("$.address.country").value("FR"))
            .andExpect(jsonPath("$.owner.email").exists())
            .andExpect(jsonPath("$.owner.givenName").exists())
            .andExpect(jsonPath("$.owner.familyName").exists())
            .andExpect(jsonPath("$.owner.locale").doesNotExist())
            .andExpect(jsonPath("$.registrationNumber").exists())
            .andRespond(withSuccess(
                """{"_links":{"clientLink":{"href":"https://my.mollie.com/dashboard/client-link/xxx"}}}""",
                MediaType.APPLICATION_JSON,
            ))

        val url = mollieConnectService.buildAuthorizationUrl(userId)

        assertTrue(url.startsWith("https://my.mollie.com/dashboard/client-link/xxx?"))
        assertTrue(url.contains("client_id=test_client"))
        assertTrue(url.contains("approval_prompt=force"))
        assertTrue(url.contains("state="))
    }

    // ── handleCallback ────────────────────────────────────────────────────────

    @Test
    fun `handleCallback - throws on unknown OAuth state (no HTTP call)`() {
        assertThrows<IllegalStateException> {
            mollieConnectService.handleCallback("code123", "nonexistent-state-uuid")
        }
    }

    @Test
    fun `handleCallback - throws on expired state and removes it from DB`() {
        val stateId = UUID.randomUUID().toString()
        mollieOAuthStateRepository.save(MollieOAuthState(
            state = stateId,
            association = association,
            expiresAt = Instant.now().minusSeconds(3600),
        ))

        assertThrows<IllegalStateException> {
            mollieConnectService.handleCallback("code123", stateId)
        }

        assertFalse(mollieOAuthStateRepository.existsById(stateId))
    }

    @Test
    fun `handleCallback - throws when Mollie org already linked to another association`() {
        val user2 = userRepository.save(
            TestFixtures.associationUser(email = "mollie2-${System.nanoTime()}@example.com")
        )
        val assoc2 = associationProfileRepository.save(
            TestFixtures.associationProfile(user2, identifier = "987654321")
        )
        mollieConnectionRepository.save(MollieConnection(
            association = assoc2,
            accessToken = "acc",
            refreshToken = "ref",
            expiresAt = Instant.now().plusSeconds(3600),
            mollieOrganizationId = "org_taken",
        ))

        val stateId = UUID.randomUUID().toString()
        mollieOAuthStateRepository.save(MollieOAuthState(
            state = stateId,
            association = association,
            expiresAt = Instant.now().plusSeconds(600),
        ))

        // All 3 HTTP calls fire before the guard check
        stubTokenExchange()
        mockServer.expect(requestTo("https://api.mollie.com/v2/organizations/me"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"org_taken"}""", MediaType.APPLICATION_JSON))
        stubOnboardingStatus()

        val ex = assertThrows<IllegalStateException> {
            mollieConnectService.handleCallback("code123", stateId)
        }
        assertTrue(ex.message!!.contains("already linked"))
    }

    @Test
    fun `handleCallback - saves connection and deletes state on success`() {
        val stateId = UUID.randomUUID().toString()
        mollieOAuthStateRepository.save(MollieOAuthState(
            state = stateId,
            association = association,
            expiresAt = Instant.now().plusSeconds(600),
        ))

        stubTokenExchange()
        mockServer.expect(requestTo("https://api.mollie.com/v2/organizations/me"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"org_xyz"}""", MediaType.APPLICATION_JSON))
        stubOnboardingStatus()

        mollieConnectService.handleCallback("code123", stateId)

        val conn = mollieConnectionRepository.findByAssociationId(associationId)
        assertNotNull(conn)
        assertEquals(MollieConnectionState.ACTIVE, conn!!.state)
        assertEquals(MollieOnboardingStatus.IN_REVIEW, conn.onboardingStatus)
        assertEquals("org_xyz", conn.mollieOrganizationId)
        assertFalse(mollieOAuthStateRepository.existsById(stateId))
    }

    // ── getConnectionStatus ───────────────────────────────────────────────────

    @Test
    fun `getConnectionStatus - returns not-connected DTO when no connection exists`() {
        val dto = mollieConnectService.getConnectionStatus(userId)

        assertFalse(dto.connected)
        assertFalse(dto.pending)
        assertFalse(dto.broken)
        assertNull(dto.onboardingStatus)
        assertNull(dto.canReceivePayments)
    }

    @Test
    fun `getConnectionStatus - re-fetches onboarding when sync is stale and returns updated status`() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "valid_token",
            refreshToken = "ref",
            expiresAt = Instant.now().plusSeconds(3600),
            onboardingStatus = MollieOnboardingStatus.IN_REVIEW,
            lastSyncedAt = Instant.now().minusSeconds(600),  // 10 min ago → stale
        ))

        // Only onboarding is fetched; token is fresh so no oauth2/tokens call
        mockServer.expect(requestTo("https://api.mollie.com/v2/onboarding/me"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """{"status":"completed","canReceivePayments":true,"canReceiveSettlements":true}""",
                MediaType.APPLICATION_JSON,
            ))

        val dto = mollieConnectService.getConnectionStatus(userId)

        assertTrue(dto.connected)
        assertEquals("COMPLETED", dto.onboardingStatus)
        assertTrue(dto.canReceivePayments!!)
    }

    @Test
    fun `getConnectionStatus - skips re-fetch when last sync is recent`() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "valid_token",
            refreshToken = "ref",
            expiresAt = Instant.now().plusSeconds(3600),
            onboardingStatus = MollieOnboardingStatus.IN_REVIEW,
            lastSyncedAt = Instant.now().minusSeconds(60),  // 1 min ago → fresh
        ))

        // No HTTP expectations → verify() confirms no call was made
        val dto = mollieConnectService.getConnectionStatus(userId)

        assertTrue(dto.connected)
        assertEquals("IN_REVIEW", dto.onboardingStatus)
    }

    @Test
    fun `getConnectionStatus - skips re-fetch when onboarding is COMPLETED`() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "valid_token",
            refreshToken = "ref",
            expiresAt = Instant.now().plusSeconds(3600),
            onboardingStatus = MollieOnboardingStatus.COMPLETED,
            canReceivePayments = true,
            lastSyncedAt = Instant.now().minusSeconds(3600),  // Stale, but COMPLETED → skip
        ))

        val dto = mollieConnectService.getConnectionStatus(userId)

        assertTrue(dto.connected)
        assertEquals("COMPLETED", dto.onboardingStatus)
        assertTrue(dto.canReceivePayments!!)
    }

    // ── MollieConnectTokenManager ─────────────────────────────────────────────

    @Test
    fun `getValidAccessToken - returns current token when not near expiry (no HTTP)`() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "current_token",
            refreshToken = "refresh_token",
            expiresAt = Instant.now().plusSeconds(3600),
        ))

        val token = tokenManager.getValidAccessToken(associationId)

        assertEquals("current_token", token)
    }

    @Test
    fun `getValidAccessToken - calls oauth2 tokens endpoint and returns new token when near expiry`() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "old_token",
            refreshToken = "old_refresh",
            expiresAt = Instant.now().plusSeconds(30),  // Within 60s safety margin → refresh
        ))

        mockServer.expect(requestTo("https://api.mollie.com/oauth2/tokens"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """{"access_token":"new_token","refresh_token":"new_refresh","expires_in":7200}""",
                MediaType.APPLICATION_JSON,
            ))

        val token = tokenManager.getValidAccessToken(associationId)

        assertEquals("new_token", token)
        assertEquals("new_token", mollieConnectionRepository.findByAssociationId(associationId)!!.accessToken)
    }

    @Test
    fun `getValidAccessToken - throws when Mollie rejects refresh token`() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "old_token",
            refreshToken = "invalid_refresh",
            expiresAt = Instant.now().plusSeconds(30),
        ))

        mockServer.expect(requestTo("https://api.mollie.com/oauth2/tokens"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        // Exception marks the shared test transaction as rollback-only;
        // no further JPA assertions are possible after this call
        assertThrows<IllegalStateException> {
            tokenManager.getValidAccessToken(associationId)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun stubTokenExchange() {
        mockServer.expect(requestTo("https://api.mollie.com/oauth2/tokens"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """{"access_token":"acc123","refresh_token":"ref456","expires_in":3600}""",
                MediaType.APPLICATION_JSON,
            ))
    }

    private fun stubOnboardingStatus() {
        mockServer.expect(requestTo("https://api.mollie.com/v2/onboarding/me"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """{"status":"in-review","canReceivePayments":false,"canReceiveSettlements":false}""",
                MediaType.APPLICATION_JSON,
            ))
    }
}

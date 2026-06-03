package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.MoneriumConfig
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MoneriumConnection
import org.commonlink.entity.MoneriumConnectionState
import org.commonlink.entity.MoneriumOAuthState
import org.commonlink.exception.MoneriumReauthRequiredException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.commonlink.repository.MoneriumOAuthStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Plain mockk-based unit test — no Spring context. The class only exercises
 * MoneriumService with fully-mocked collaborators, so @SpringBootTest would
 * just load the full app context (including unrelated Web3j beans) for no gain.
 */
class MoneriumServiceTest {

    private val config = MoneriumConfig(
        clientId = "test-client-id",
        baseUrl = "https://sandbox.monerium.app",
        redirectUri = "http://localhost:8080/api/monerium/callback",
    )

    private val stateRepo: MoneriumOAuthStateRepository = mockk()
    private val connectionRepo: MoneriumConnectionRepository = mockk()
    private val associationRepo: AssociationProfileRepository = mockk()
    private val restTemplate: RestTemplate = mockk()

    private val service = MoneriumService(config, stateRepo, connectionRepo, associationRepo, restTemplate)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val mockAssociation: AssociationProfile = mockk(relaxed = true)

    private val sampleState = MoneriumOAuthState(
        state = "test-state-uuid",
        codeVerifier = "test-code-verifier-min-43-chars-padded-here-xxxx",
        association = mockAssociation,
        expiresAt = Instant.now().plusSeconds(600),
    )

    private val sampleTokenResponse = MoneriumService.TokenResponse(
        accessToken = "access-token-123",
        refreshToken = "refresh-token-456",
        expiresIn = 3600,
        userId = "monerium-user-789",
    )

    @BeforeEach
    fun setupCommonMocks() {
        every { associationRepo.findByUserId(userId) } returns Optional.of(mockAssociation)
    }

    // ── buildAuthorizationUrl ─────────────────────────────────────────────────

    @Test
    fun `buildAuthorizationUrl - returns URL with required PKCE parameters`() {
        every { stateRepo.save(any()) } answers { firstArg() }

        val url = service.buildAuthorizationUrl(userId)

        assertTrue(url.startsWith("https://sandbox.monerium.app/auth?"))
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("scope=openid"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge="))
        assertTrue(url.contains("state="))
    }

    @Test
    fun `buildAuthorizationUrl - persists OAuth state with code verifier and TTL`() {
        every { stateRepo.save(any()) } answers { firstArg() }

        service.buildAuthorizationUrl(userId)

        verify {
            stateRepo.save(match { state ->
                state.codeVerifier.isNotBlank() &&
                    state.association == mockAssociation &&
                    state.expiresAt.isAfter(Instant.now())
            })
        }
    }

    @Test
    fun `buildAuthorizationUrl - throws IllegalArgumentException when association not found`() {
        val unknownId = UUID.randomUUID()
        every { associationRepo.findByUserId(unknownId) } returns Optional.empty()

        assertThrows<IllegalArgumentException> {
            service.buildAuthorizationUrl(unknownId)
        }
    }

    // ── handleCallback ────────────────────────────────────────────────────────

    @Test
    fun `handleCallback - exchanges code, saves connection, deletes state`() {
        every { stateRepo.findById("test-state-uuid") } returns Optional.of(sampleState)
        every {
            restTemplate.postForEntity(any<String>(), any(), eq(MoneriumService.TokenResponse::class.java))
        } returns ResponseEntity.ok(sampleTokenResponse)
        every { connectionRepo.save(any()) } answers { firstArg() }
        justRun { stateRepo.delete(sampleState) }

        val result = service.handleCallback("auth-code-abc", "test-state-uuid")

        verify { stateRepo.delete(sampleState) }
        verify { connectionRepo.save(any()) }
        assertEquals("access-token-123", result.accessToken)
        assertEquals("refresh-token-456", result.refreshToken)
        assertEquals("monerium-user-789", result.moneriumUserId)
        assertNotNull(result.expiresAt)
    }

    @Test
    fun `handleCallback - throws IllegalArgumentException for unknown state`() {
        every { stateRepo.findById("unknown-state") } returns Optional.empty()

        assertThrows<IllegalArgumentException> {
            service.handleCallback("code", "unknown-state")
        }
    }

    @Test
    fun `handleCallback - throws IllegalStateException and deletes state when expired`() {
        val expiredState = MoneriumOAuthState(
            state = "expired-state",
            codeVerifier = "verifier",
            association = mockAssociation,
            expiresAt = Instant.now().minusSeconds(1),
        )
        every { stateRepo.findById("expired-state") } returns Optional.of(expiredState)
        justRun { stateRepo.delete(expiredState) }

        assertThrows<IllegalStateException> {
            service.handleCallback("code", "expired-state")
        }

        verify { stateRepo.delete(expiredState) }
    }

    @Test
    fun `handleCallback - throws IllegalStateException when token response body is null`() {
        every { stateRepo.findById("test-state-uuid") } returns Optional.of(sampleState)
        every {
            restTemplate.postForEntity(any<String>(), any(), eq(MoneriumService.TokenResponse::class.java))
        } returns ResponseEntity.ok(null)
        justRun { stateRepo.delete(any()) }

        assertThrows<IllegalStateException> {
            service.handleCallback("code", "test-state-uuid")
        }
    }

    // ── getConnectionStatus ───────────────────────────────────────────────────

    @Test
    fun `getConnectionStatus - returns connected=true and pending=false when connection exists`() {
        every { connectionRepo.findByAssociation(mockAssociation) } returns mockk(relaxed = true)

        val result = service.getConnectionStatus(userId)
        assertTrue(result.connected)
        assertFalse(result.pending)
    }

    @Test
    fun `getConnectionStatus - returns pending=true when OAuth state exists and no connection`() {
        every { connectionRepo.findByAssociation(mockAssociation) } returns null
        every { stateRepo.existsByAssociationAndExpiresAtAfter(mockAssociation, any()) } returns true

        val result = service.getConnectionStatus(userId)
        assertFalse(result.connected)
        assertTrue(result.pending)
    }

    @Test
    fun `getConnectionStatus - returns connected=false and pending=false when nothing exists`() {
        every { connectionRepo.findByAssociation(mockAssociation) } returns null
        every { stateRepo.existsByAssociationAndExpiresAtAfter(mockAssociation, any()) } returns false

        val result = service.getConnectionStatus(userId)
        assertFalse(result.connected)
        assertFalse(result.pending)
    }

    @Test
    fun `getConnectionStatus - throws IllegalArgumentException when association not found`() {
        val unknownId = UUID.randomUUID()
        every { associationRepo.findByUserId(unknownId) } returns Optional.empty()

        assertThrows<IllegalArgumentException> {
            service.getConnectionStatus(unknownId)
        }
    }

    // ── getValidAccessToken / forceRefreshAccessToken ─────────────────────────

    private val associationId = UUID.fromString("00000000-0000-0000-0000-000000000aaa")

    private fun connectionFixture(
        accessToken: String = "access-old",
        refreshToken: String = "refresh-old",
        expiresIn: Long = 3600,
        state: MoneriumConnectionState = MoneriumConnectionState.ACTIVE,
    ) = MoneriumConnection(
        association = mockAssociation,
        moneriumUserId = "monerium-user",
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = Instant.now().plusSeconds(expiresIn),
        state = state,
    )

    @Test
    fun `getValidAccessToken - returns cached token when expiry is well beyond safety margin`() {
        val conn = connectionFixture(expiresIn = 3600)
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns conn

        val token = service.getValidAccessToken(associationId)

        assertEquals("access-old", token)
        verify(exactly = 0) {
            restTemplate.postForEntity(any<String>(), any(), eq(MoneriumService.TokenResponse::class.java))
        }
    }

    @Test
    fun `getValidAccessToken - refreshes when token within safety margin and persists rotated pair`() {
        val conn = connectionFixture(expiresIn = 10) // < 30s margin
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns conn
        every {
            restTemplate.postForEntity(any<String>(), any(), eq(MoneriumService.TokenResponse::class.java))
        } returns ResponseEntity.ok(
            MoneriumService.TokenResponse(
                accessToken = "access-new",
                refreshToken = "refresh-new",
                expiresIn = 3600,
                userId = null,
            )
        )
        every { connectionRepo.save(any()) } answers { firstArg() }

        val token = service.getValidAccessToken(associationId)

        assertEquals("access-new", token)
        verify {
            connectionRepo.save(match { c: MoneriumConnection ->
                c.accessToken == "access-new" && c.refreshToken == "refresh-new"
            })
        }
    }

    @Test
    fun `getValidAccessToken - throws reauth required when connection is BROKEN`() {
        val conn = connectionFixture(state = MoneriumConnectionState.BROKEN)
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns conn

        assertThrows<MoneriumReauthRequiredException> {
            service.getValidAccessToken(associationId)
        }
    }

    @Test
    fun `getValidAccessToken - throws reauth required when no connection exists`() {
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns null

        assertThrows<MoneriumReauthRequiredException> {
            service.getValidAccessToken(associationId)
        }
    }

    @Test
    fun `forceRefreshAccessToken - refreshes regardless of stored expiry`() {
        val conn = connectionFixture(expiresIn = 3600) // would be cached by getValidAccessToken
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns conn
        every {
            restTemplate.postForEntity(any<String>(), any(), eq(MoneriumService.TokenResponse::class.java))
        } returns ResponseEntity.ok(
            MoneriumService.TokenResponse(
                accessToken = "access-forced",
                refreshToken = "refresh-forced",
                expiresIn = 3600,
                userId = null,
            )
        )
        every { connectionRepo.save(any()) } answers { firstArg() }

        val token = service.forceRefreshAccessToken(associationId)

        assertEquals("access-forced", token)
    }

    @Test
    fun `refreshTokens - flags connection BROKEN and throws on invalid_grant`() {
        val conn = connectionFixture(expiresIn = 10)
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns conn
        every {
            restTemplate.postForEntity(any<String>(), any(), eq(MoneriumService.TokenResponse::class.java))
        } throws HttpClientErrorException(HttpStatus.BAD_REQUEST, "invalid_grant")
        every { connectionRepo.save(any()) } answers { firstArg() }

        assertThrows<MoneriumReauthRequiredException> {
            service.getValidAccessToken(associationId)
        }

        verify {
            connectionRepo.save(match { c: MoneriumConnection -> c.state == MoneriumConnectionState.BROKEN })
        }
    }
}

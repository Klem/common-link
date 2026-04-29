package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.MoneriumConfig
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MoneriumOAuthState
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.commonlink.repository.MoneriumOAuthStateRepository
import org.commonlink.repository.TestcontainersConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Optional
import java.util.UUID

@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
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
}

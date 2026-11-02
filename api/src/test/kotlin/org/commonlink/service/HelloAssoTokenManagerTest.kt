package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.repository.HelloAssoConnectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/** Pure unit tests of [HelloAssoTokenManager]: encrypted-cache hit, renewal, and failures that write nothing. */
class HelloAssoTokenManagerTest {

    private val repo = mockk<HelloAssoConnectionRepository>()
    private val client = mockk<HelloAssoClient>()
    private val manager = HelloAssoTokenManager(repo, client)
    private val associationId = UUID.randomUUID()

    private fun connection(
        state: HelloAssoConnectionState = HelloAssoConnectionState.ACTIVE,
        expiresAt: Instant? = Instant.now().plusSeconds(1200),
    ) = HelloAssoConnection(
        association = mockk<AssociationProfile>(relaxed = true),
        webhookToken = "wh",
        state = state,
        clientId = "client_123",
        refreshToken = "old-refresh",
        accessToken = "cached-access",
        accessTokenExpiresAt = expiresAt,
    )

    @Test
    fun `a cached token valid beyond the margin is returned without any call`() {
        every { repo.findByAssociationIdForUpdate(associationId) } returns connection()

        assertEquals("cached-access", manager.getValidAccessToken(associationId))
        verify(exactly = 0) { client.refreshTokens(any()) }
    }

    @Test
    fun `a token expiring within the margin is renewed and both tokens are stored`() {
        val connection = connection(expiresAt = Instant.now().plusSeconds(30))
        every { repo.findByAssociationIdForUpdate(associationId) } returns connection
        every { client.refreshTokens("old-refresh") } returns HelloAssoTokens("new-access", "new-refresh", 1800)
        every { repo.save(connection) } returns connection

        assertEquals("new-access", manager.getValidAccessToken(associationId))
        assertEquals("new-access", connection.accessToken)
        assertEquals("new-refresh", connection.refreshToken)
        assertNotNull(connection.lastRefreshAt)
        assert(connection.accessTokenExpiresAt!!.isAfter(Instant.now().plusSeconds(1700)))
        verify { repo.save(connection) }
    }

    @Test
    fun `a missing cache is renewed`() {
        val connection = connection(expiresAt = null)
        every { repo.findByAssociationIdForUpdate(associationId) } returns connection
        every { client.refreshTokens(any()) } returns HelloAssoTokens("new-access", "new-refresh", 1800)
        every { repo.save(connection) } returns connection

        assertEquals("new-access", manager.getValidAccessToken(associationId))
    }

    @Test
    fun `a rejected refresh token surfaces and nothing is written`() {
        val connection = connection(expiresAt = Instant.now().minusSeconds(10))
        every { repo.findByAssociationIdForUpdate(associationId) } returns connection
        every { client.refreshTokens(any()) } throws HelloAssoUnauthorizedException("refresh_token", 400)

        assertThrows<HelloAssoUnauthorizedException> { manager.getValidAccessToken(associationId) }
        verify(exactly = 0) { repo.save(any()) }
        assertEquals("old-refresh", connection.refreshToken)
    }

    @Test
    fun `a link that is not ACTIVE is refused without any call`() {
        every { repo.findByAssociationIdForUpdate(associationId) } returns connection(state = HelloAssoConnectionState.BROKEN)

        assertThrows<IllegalStateException> { manager.getValidAccessToken(associationId) }
        verify(exactly = 0) { client.refreshTokens(any()) }
    }

    @Test
    fun `an association without link is refused`() {
        every { repo.findByAssociationIdForUpdate(associationId) } returns null

        assertThrows<IllegalStateException> { manager.getValidAccessToken(associationId) }
    }
}

package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.config.MollieConnectConfig
import org.commonlink.exception.MollieRefreshRejectedException
import org.commonlink.exception.MollieRefreshUnavailableException
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieRefreshCandidate
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [MollieTokenRefreshExecutor].
 *
 * The behaviours pinned here are the ones whose regression would be silent in production: a
 * transient Mollie error must never brick a connection, a definitive rejection must break it, mock
 * rows must not be presented to Mollie, and one dead association must not stop the sweep. The
 * persistence of the BROKEN state is covered separately in [MollieConnectionBreakerTest].
 */
class MollieTokenRefreshExecutorTest {

    private val connectionRepo: MollieConnectionRepository = mockk()
    private val tokenManager: MollieConnectTokenManager = mockk()
    private val connectionBreaker: MollieConnectionBreaker = mockk(relaxed = true)

    private val lookahead = 1_800L
    private val config = MollieConnectConfig(
        clientId = "app_test",
        clientSecret = "secret",
        redirectUri = "https://api.test/callback",
        scopes = "payments.read",
        tokenRefresh = MollieConnectConfig.TokenRefresh(lookaheadSeconds = lookahead),
    )

    private val executor =
        MollieTokenRefreshExecutor(connectionRepo, tokenManager, connectionBreaker, config)

    private fun candidate(associationId: UUID, refreshToken: String = "refresh_real_token") =
        MollieRefreshCandidate(associationId, refreshToken)

    @Test
    fun `refreshes every candidate through the locked expiry-rechecking path`() {
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        every { connectionRepo.findActiveExpiringBefore(any()) } returns
            listOf(candidate(firstId), candidate(secondId))
        every { tokenManager.getValidAccessToken(any(), any()) } returns "access_new"

        executor.sweep()

        verify(exactly = 1) { tokenManager.getValidAccessToken(firstId, lookahead) }
        verify(exactly = 1) { tokenManager.getValidAccessToken(secondId, lookahead) }
        verify(exactly = 0) { connectionBreaker.markBroken(any(), any()) }
    }

    @Test
    fun `queries with the configured lookahead window`() {
        val threshold = slot<Instant>()
        every { connectionRepo.findActiveExpiringBefore(capture(threshold)) } returns emptyList()

        val before = Instant.now()
        executor.sweep()

        // Must look far enough ahead to catch tokens expiring before the next tick.
        assertTrue(threshold.captured.isAfter(before.plusSeconds(lookahead - 5)))
        verify(exactly = 0) { tokenManager.getValidAccessToken(any(), any()) }
    }

    @Test
    fun `never presents a mock refresh token to Mollie`() {
        every { connectionRepo.findActiveExpiringBefore(any()) } returns
            listOf(candidate(UUID.randomUUID(), refreshToken = MOCK_TOKEN_SENTINEL))

        executor.sweep()

        verify(exactly = 0) { tokenManager.getValidAccessToken(any(), any()) }
        verify(exactly = 0) { connectionBreaker.markBroken(any(), any()) }
    }

    @Test
    fun `leaves the connection alone when Mollie is merely unavailable`() {
        every { connectionRepo.findActiveExpiringBefore(any()) } returns listOf(candidate(UUID.randomUUID()))
        every { tokenManager.getValidAccessToken(any(), any()) } throws
            MollieRefreshUnavailableException(HttpStatus.SERVICE_UNAVAILABLE)

        executor.sweep()

        // A Mollie outage must not force every association through a fresh OAuth flow.
        verify(exactly = 0) { connectionBreaker.markBroken(any(), any()) }
    }

    @Test
    fun `treats throttling as transient, not as a dead grant`() {
        every { connectionRepo.findActiveExpiringBefore(any()) } returns listOf(candidate(UUID.randomUUID()))
        every { tokenManager.getValidAccessToken(any(), any()) } throws
            MollieRefreshUnavailableException(HttpStatus.TOO_MANY_REQUESTS)

        executor.sweep()

        verify(exactly = 0) { connectionBreaker.markBroken(any(), any()) }
    }

    @Test
    fun `breaks the connection on a definitive rejection`() {
        val associationId = UUID.randomUUID()
        every { connectionRepo.findActiveExpiringBefore(any()) } returns listOf(candidate(associationId))
        every { tokenManager.getValidAccessToken(any(), any()) } throws
            MollieRefreshRejectedException(HttpStatus.BAD_REQUEST, """{"error":"invalid_grant"}""")

        executor.sweep()

        verify(exactly = 1) { connectionBreaker.markBroken(associationId, 400) }
    }

    @Test
    fun `one dead association does not stop the others from being refreshed`() {
        val deadId = UUID.randomUUID()
        val healthyId = UUID.randomUUID()
        every { connectionRepo.findActiveExpiringBefore(any()) } returns
            listOf(candidate(deadId), candidate(healthyId))
        every { tokenManager.getValidAccessToken(deadId, lookahead) } throws
            MollieRefreshRejectedException(HttpStatus.BAD_REQUEST, """{"error":"invalid_grant"}""")
        every { tokenManager.getValidAccessToken(healthyId, lookahead) } returns "access_new"

        executor.sweep()

        verify(exactly = 1) { connectionBreaker.markBroken(deadId, 400) }
        verify(exactly = 1) { tokenManager.getValidAccessToken(healthyId, lookahead) }
    }

    @Test
    fun `sweep never throws when a connection disappears between query and lock`() {
        val associationId = UUID.randomUUID()
        every { connectionRepo.findActiveExpiringBefore(any()) } returns listOf(candidate(associationId))
        every { tokenManager.getValidAccessToken(any(), any()) } throws
            IllegalStateException("No Mollie connection for association $associationId")

        executor.sweep()

        verify(exactly = 0) { connectionBreaker.markBroken(any(), any()) }
    }
}

package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.HelloAssoProperties
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.event.HelloAssoConnectionBrokenEvent
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** Scheduled renewal, BROKEN transition and its email (pure unit tests). */
class HelloAssoTokenRefreshExecutorTest {

    private val repo = mockk<HelloAssoConnectionRepository>()
    private val tokenManager = mockk<HelloAssoTokenManager>()
    private val breaker = mockk<HelloAssoConnectionBreaker>()
    private val executor = HelloAssoTokenRefreshExecutor(repo, tokenManager, breaker, HelloAssoProperties())

    @Test
    fun `no active HelloAsso link - exits after one query, the gateway never reached`() {
        every { repo.existsActiveForHelloAssoAssociations() } returns false

        executor.sweep()

        verify(exactly = 0) { repo.findActiveHelloAssoRefreshCandidates(any()) }
        verify(exactly = 0) { tokenManager.refreshIfOlderThan(any(), any()) }
    }

    @Test
    fun `due links are renewed with the weekly threshold - a rejected one is broken, an unavailable one deferred`() {
        val ok = UUID.randomUUID()
        val rejected = UUID.randomUUID()
        val down = UUID.randomUUID()
        every { repo.existsActiveForHelloAssoAssociations() } returns true
        every { repo.findActiveHelloAssoRefreshCandidates(any()) } returns listOf(ok, rejected, down)
        every { tokenManager.refreshIfOlderThan(ok, any()) } returns true
        every { tokenManager.refreshIfOlderThan(rejected, any()) } throws HelloAssoUnauthorizedException("refresh_token", 401)
        every { tokenManager.refreshIfOlderThan(down, any()) } throws HelloAssoUnavailableException("refresh_token", 503)
        justRun { breaker.markBroken(rejected) }

        assertDoesNotThrow { executor.sweep() }

        verify(exactly = 1) { breaker.markBroken(rejected) }
        verify(exactly = 0) { breaker.markBroken(ok) }
        verify(exactly = 0) { breaker.markBroken(down) }
        verify {
            repo.findActiveHelloAssoRefreshCandidates(match { it.isBefore(Instant.now().minusSeconds(6 * 24 * 3600)) })
        }
    }

    @Test
    fun `breaker - ACTIVE becomes BROKEN and is announced once`() {
        val events = mockk<ApplicationEventPublisher>(relaxed = true)
        val associationId = UUID.randomUUID()
        val row = HelloAssoConnection(
            association = mockk<AssociationProfile>(relaxed = true), webhookToken = "t",
            state = HelloAssoConnectionState.ACTIVE, canCollect = true,
        )
        every { repo.findByAssociationIdForUpdate(associationId) } returns row
        every { repo.save(row) } returns row
        val breaker = HelloAssoConnectionBreaker(repo, events)

        breaker.markBroken(associationId)
        breaker.markBroken(associationId)

        assertEquals(HelloAssoConnectionState.BROKEN, row.state)
        assertFalse(row.canCollect)
        verify(exactly = 1) { events.publishEvent(HelloAssoConnectionBrokenEvent(associationId)) }
    }

    @Test
    fun `email listener - sends the relink email to the contact address`() {
        val emailService = mockk<EmailService>(relaxed = true)
        val associations = mockk<AssociationProfileRepository>()
        val associationId = UUID.randomUUID()
        val profile = mockk<AssociationProfile> {
            every { name } returns "Techno Plus"
            every { contactEmail } returns "contact@techno.org"
        }
        every { associations.findById(associationId) } returns Optional.of(profile)

        HelloAssoEmailListener(emailService, associations, "https://app.test").onConnectionBroken(HelloAssoConnectionBrokenEvent(associationId))

        verify(exactly = 1) {
            emailService.sendHelloAssoConnectionBroken(
                "Techno Plus", "contact@techno.org", "https://app.test/fr/dashboard/association/profile?tab=bank",
            )
        }
    }
}

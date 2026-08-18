package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.event.MollieConnectionBrokenEvent
import org.commonlink.repository.MollieConnectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [MollieConnectionBreaker].
 *
 * This is the write that the old in-place `state = BROKEN` never managed to commit, so what matters
 * here is that it persists, takes the row lock, and warns the association exactly once.
 */
class MollieConnectionBreakerTest {

    private val connectionRepo: MollieConnectionRepository = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val breaker = MollieConnectionBreaker(connectionRepo, eventPublisher)

    private val associationId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        // JpaRepository.save is generic; a relaxed mock would hand back a bare Object.
        every { connectionRepo.save(any()) } answers { firstArg() }
    }

    private fun connection(state: MollieConnectionState = MollieConnectionState.ACTIVE): MollieConnection {
        val association: AssociationProfile = mockk()
        every { association.id } returns associationId
        return MollieConnection(
            association = association,
            accessToken = "access_real_token",
            refreshToken = "refresh_real_token",
            expiresAt = Instant.now().plusSeconds(60),
            state = state,
        )
    }

    @Test
    fun `persists BROKEN under the row lock and warns the association`() {
        val conn = connection()
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns conn

        breaker.markBroken(associationId, 400)

        assertEquals(MollieConnectionState.BROKEN, conn.state)
        // The lock-acquiring read, not the plain one — two instances must not both flip and notify.
        verify(exactly = 1) { connectionRepo.findByAssociationIdForUpdate(associationId) }
        verify(exactly = 1) { connectionRepo.save(conn) }
        verify(exactly = 1) { eventPublisher.publishEvent(MollieConnectionBrokenEvent(associationId)) }
    }

    @Test
    fun `does not re-notify an already BROKEN connection`() {
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns
            connection(MollieConnectionState.BROKEN)

        breaker.markBroken(associationId, 400)

        verify(exactly = 0) { connectionRepo.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<MollieConnectionBrokenEvent>()) }
    }

    @Test
    fun `is a no-op when the connection has disappeared`() {
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns null

        breaker.markBroken(associationId, 400)

        verify(exactly = 0) { connectionRepo.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<MollieConnectionBrokenEvent>()) }
    }
}

package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MoneriumConnection
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Plain mockk unit test for AssociationService — no Spring context.
 */
class AssociationServiceTest {

    private val associationRepo: AssociationProfileRepository = mockk()
    private val connectionRepo: MoneriumConnectionRepository = mockk()
    private val outbox: OnchainOutboxService = mockk()

    private val service = AssociationService(associationRepo, connectionRepo, outbox)

    private val associationId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val walletAddress = "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"

    private val mockAssociation: AssociationProfile = mockk(relaxed = true)
    private val mockConnection: MoneriumConnection = mockk(relaxed = true)

    private fun stubAssociation() {
        every { mockAssociation.id } returns associationId
        every { mockAssociation.identifier } returns "123456789"
        every { mockAssociation.verified = any() } returns Unit
        every { associationRepo.findById(associationId) } returns Optional.of(mockAssociation)
        every { associationRepo.save(mockAssociation) } returns mockAssociation
    }

    private fun stubConnection(address: String?) {
        every { mockConnection.walletAddress } returns address
        every { connectionRepo.findByAssociationId(associationId) } returns if (address != null) mockConnection else null
    }

    private fun stubOutbox() {
        every { outbox.enqueue(any(), any(), any()) } answers {
            org.commonlink.entity.OnchainJob(
                action = firstArg(),
                payloadJson = "{}",
                correlationKey = thirdArg(),
                status = OnchainJobStatus.PENDING,
            )
        }
    }

    // ── markVerified ──────────────────────────────────────────────────────────

    @Test
    fun `markVerified - enqueues VERIFY_ASSOCIATION when wallet is present`() {
        stubAssociation()
        stubConnection(walletAddress)
        stubOutbox()

        service.markVerified(associationId)

        val actionSlot = slot<OnchainJobAction>()
        val keySlot = slot<String>()
        verify { outbox.enqueue(capture(actionSlot), any(), capture(keySlot)) }
        assertEquals(OnchainJobAction.VERIFY_ASSOCIATION, actionSlot.captured)
        assertEquals("VERIFY_ASSOCIATION:$associationId", keySlot.captured)
    }

    @Test
    fun `markVerified - calling twice enqueues only one job (idempotency via correlationKey)`() {
        stubAssociation()
        stubConnection(walletAddress)
        // First call returns new job; second call returns the same job (idempotency in outbox)
        val existingJob = org.commonlink.entity.OnchainJob(
            action = OnchainJobAction.VERIFY_ASSOCIATION,
            payloadJson = "{}",
            correlationKey = "VERIFY_ASSOCIATION:$associationId",
        )
        every { outbox.enqueue(OnchainJobAction.VERIFY_ASSOCIATION, any(), "VERIFY_ASSOCIATION:$associationId") } returns existingJob

        service.markVerified(associationId)
        service.markVerified(associationId)

        // outbox.enqueue is called twice but the outbox itself deduplicates via correlationKey
        verify(exactly = 2) { outbox.enqueue(OnchainJobAction.VERIFY_ASSOCIATION, any(), "VERIFY_ASSOCIATION:$associationId") }
    }

    @Test
    fun `markVerified - skips enqueue when no wallet is linked`() {
        stubAssociation()
        stubConnection(null)

        service.markVerified(associationId)

        verify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
    }

    // ── revokeAssociation ─────────────────────────────────────────────────────

    @Test
    fun `revokeAssociation - enqueues REVOKE_ASSOCIATION when wallet is present`() {
        stubAssociation()
        stubConnection(walletAddress)
        stubOutbox()

        service.revokeAssociation(associationId)

        val actionSlot = slot<OnchainJobAction>()
        val keySlot = slot<String>()
        verify { outbox.enqueue(capture(actionSlot), any(), capture(keySlot)) }
        assertEquals(OnchainJobAction.REVOKE_ASSOCIATION, actionSlot.captured)
        assertEquals("REVOKE_ASSOCIATION:$associationId", keySlot.captured)
    }

    @Test
    fun `revokeAssociation - skips enqueue when no wallet is linked`() {
        stubAssociation()
        stubConnection(null)

        service.revokeAssociation(associationId)

        verify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
    }

    // ── restoreAssociation ────────────────────────────────────────────────────

    @Test
    fun `restoreAssociation - enqueues RESTORE_ASSOCIATION when wallet is present`() {
        stubAssociation()
        stubConnection(walletAddress)
        stubOutbox()

        service.restoreAssociation(associationId)

        val actionSlot = slot<OnchainJobAction>()
        val keySlot = slot<String>()
        verify { outbox.enqueue(capture(actionSlot), any(), capture(keySlot)) }
        assertEquals(OnchainJobAction.RESTORE_ASSOCIATION, actionSlot.captured)
        assertEquals("RESTORE_ASSOCIATION:$associationId", keySlot.captured)
    }

    @Test
    fun `restoreAssociation - skips enqueue when no wallet is linked`() {
        stubAssociation()
        stubConnection(null)

        service.restoreAssociation(associationId)

        verify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
    }
}

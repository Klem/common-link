package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.dto.HelloAssoConnectRequest
import org.commonlink.dto.HelloAssoMandateDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.HelloAssoMandateRequiredException
import org.commonlink.exception.HelloAssoNotAssociationProviderException
import org.commonlink.exception.HelloAssoVerificationExpiredException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.commonlink.repository.NotPaidOutTotal
import org.commonlink.dto.HelloAssoDisconnectPreviewResponse
import java.math.BigDecimal
import org.commonlink.repository.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** [HelloAssoLinkService]: link with the mandate (no secret, stable token, journal), disconnection. */
class HelloAssoLinkServiceTest {

    private val associationRepo = mockk<AssociationProfileRepository>()
    private val connectionRepo = mockk<HelloAssoConnectionRepository>()
    private val store = HelloAssoPendingLinkStore()
    private val auditLog = mockk<ComplianceAuditLogService>(relaxed = true)
    private val fundsRepo = mockk<HelloAssoDonationFundsRepository>()
    private val service = HelloAssoLinkService(associationRepo, connectionRepo, store, HelloAssoConnectionRows(connectionRepo), auditLog, fundsRepo)

    private val userId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()
    private lateinit var association: AssociationProfile
    private lateinit var row: HelloAssoConnection
    private val mandate = HelloAssoMandateDto(accepted = true, version = HelloAssoLinkService.MANDATE_VERSION)

    @BeforeEach
    fun setUp() {
        association = AssociationProfile(
            id = associationId, user = TestFixtures.associationUser(), name = "Techno Plus", identifier = "W751121684",
            paymentProvider = PaymentProviderId.HELLOASSO,
        )
        row = HelloAssoConnection(association = association, webhookToken = "stable-token")
        every { associationRepo.findByUserId(userId) } returns Optional.of(association)
        every { connectionRepo.findByAssociationId(associationId) } returns row
        every { connectionRepo.findByAssociationIdForUpdate(associationId) } returns row
        every { connectionRepo.save(any()) } answers { firstArg() }
        every { fundsRepo.sumNotPaidOutByAssociationId(associationId) } returns NotPaidOutTotal(0, null)
    }

    private fun verified(canCollect: Boolean = true, owner: UUID = associationId) = store.put(
        HelloAssoPendingLink(
            owner, "3c3ffc099dc543fb", "techno-plus", "Techno Plus", canCollect,
            HelloAssoTokens("access", "refresh", 1800), Instant.now(),
        )
    )

    @Test
    fun `connect persists the link on the existing row, keeps the token, and journals the versioned mandate`() {
        val payload = slot<Any>()

        service.connect(userId, HelloAssoConnectRequest(verified(), mandate))

        assertEquals(HelloAssoConnectionState.ACTIVE, row.state)
        assertEquals("stable-token", row.webhookToken)
        assertEquals("techno-plus", row.organizationSlug)
        assertEquals("3c3ffc099dc543fb", row.clientId)
        assertEquals("refresh", row.refreshToken)
        assertTrue(row.canCollect)
        assertTrue(row.everCompliant)
        assertNotNull(row.secretWipedAt)
        assertEquals(userId, row.linkedBy)
        verify {
            auditLog.append(
                ComplianceAuditLogService.HELLOASSO_MANDATE_ACCEPTED, ComplianceAuditSubjectType.ASSOCIATION,
                capture(payload), associationId, userId,
            )
        }
        assertEquals(
            mapOf("version" to HelloAssoLinkService.MANDATE_VERSION, "organizationSlug" to "techno-plus", "clientIdMasked" to "3c3f…43fb"),
            payload.captured,
        )
    }

    @Test
    fun `connect relinks a BROKEN row and keeps everCompliant`() {
        row.state = HelloAssoConnectionState.BROKEN
        row.everCompliant = true

        service.connect(userId, HelloAssoConnectRequest(verified(canCollect = false), mandate))

        assertEquals(HelloAssoConnectionState.ACTIVE, row.state)
        assertTrue(row.everCompliant)
    }

    @Test
    fun `a mandate not accepted or in another version is refused before anything is used`() {
        val verifyId = verified()

        assertThrows<HelloAssoMandateRequiredException> {
            service.connect(userId, HelloAssoConnectRequest(verifyId, mandate.copy(accepted = false)))
        }
        assertThrows<HelloAssoMandateRequiredException> {
            service.connect(userId, HelloAssoConnectRequest(verifyId, mandate.copy(version = "helloasso-mandate-v1")))
        }
        // The verification is still usable once the right mandate is given.
        service.connect(userId, HelloAssoConnectRequest(verifyId, mandate))
        assertEquals(HelloAssoConnectionState.ACTIVE, row.state)
    }

    @Test
    fun `an unknown, used or other association's verification is refused`() {
        val verifyId = verified()
        service.connect(userId, HelloAssoConnectRequest(verifyId, mandate))

        assertThrows<HelloAssoVerificationExpiredException> { service.connect(userId, HelloAssoConnectRequest(verifyId, mandate)) }
        assertThrows<HelloAssoVerificationExpiredException> {
            service.connect(userId, HelloAssoConnectRequest(verified(owner = UUID.randomUUID()), mandate))
        }
    }

    @Test
    fun `an association not on HelloAsso cannot link`() {
        association.paymentProvider = PaymentProviderId.MOLLIE

        assertThrows<HelloAssoNotAssociationProviderException> { service.connect(userId, HelloAssoConnectRequest(verified(), mandate)) }
        verify(exactly = 0) { connectionRepo.save(any()) }
    }

    @Test
    fun `disconnect erases the tokens, keeps row and token, and journals it`() {
        service.connect(userId, HelloAssoConnectRequest(verified(), mandate))

        service.disconnect(userId)

        assertEquals(HelloAssoConnectionState.DISCONNECTED, row.state)
        assertNull(row.refreshToken)
        assertNull(row.accessToken)
        assertEquals("stable-token", row.webhookToken)
        verify { auditLog.append(ComplianceAuditLogService.HELLOASSO_DISCONNECTED, any(), any(), associationId, userId) }
    }

    @Test
    fun `disconnect is idempotent and allowed whatever the provider`() {
        association.paymentProvider = PaymentProviderId.MOLLIE
        row.state = HelloAssoConnectionState.DISCONNECTED

        service.disconnect(userId)

        verify(exactly = 0) { connectionRepo.save(any()) }
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `disconnect journals the donations HelloAsso has not paid out yet, and still disconnects`() {
        service.connect(userId, HelloAssoConnectRequest(verified(), mandate))
        every { fundsRepo.sumNotPaidOutByAssociationId(associationId) } returns NotPaidOutTotal(3, BigDecimal("42.50"))
        val payload = slot<Any>()

        service.disconnect(userId)

        assertEquals(HelloAssoConnectionState.DISCONNECTED, row.state)
        verify { auditLog.append(ComplianceAuditLogService.HELLOASSO_DISCONNECTED, ComplianceAuditSubjectType.ASSOCIATION, capture(payload), associationId, userId) }
        val journaled = payload.captured as Map<*, *>
        assertEquals(3L, journaled["notYetPaidOutCount"])
        assertEquals("42.50", journaled["notYetPaidOutAmount"])
    }

    @Test
    fun `disconnect preview gives the not-paid-out count and amount, zero when none`() {
        assertEquals(HelloAssoDisconnectPreviewResponse(0, BigDecimal("0.00")), service.disconnectPreview(userId))

        every { fundsRepo.sumNotPaidOutByAssociationId(associationId) } returns NotPaidOutTotal(2, BigDecimal("15.00"))
        assertEquals(HelloAssoDisconnectPreviewResponse(2, BigDecimal("15.00")), service.disconnectPreview(userId))
    }
}

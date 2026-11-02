package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.config.DonationCapProperties
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.event.PaymentProviderChangedEvent
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.payment.PaymentProvider
import org.commonlink.payment.PaymentProviderId
import org.commonlink.payment.PaymentProviderRegistry
import org.commonlink.payment.ProviderReadiness
import org.commonlink.payment.ProviderReadinessStatus
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID

/** Pure unit tests of [PaymentProviderChangeService]. */
class PaymentProviderChangeServiceTest {

    private val associationRepo = mockk<AssociationProfileRepository>(relaxed = true)
    private val campaignRepo = mockk<CampaignRepository>()
    private val donationRepo = mockk<DonationRepository>()
    private val registry = mockk<PaymentProviderRegistry>()
    private val auditLog = mockk<ComplianceAuditLogService>(relaxed = true)
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = PaymentProviderChangeService(
        associationRepo, campaignRepo, donationRepo, registry, auditLog, events, DonationCapProperties(),
    )

    private val associationId = UUID.randomUUID()
    private val curatorId = UUID.randomUUID()
    private lateinit var association: AssociationProfile

    @BeforeEach
    fun setUp() {
        association = AssociationProfile(
            id = associationId,
            user = TestFixtures.associationUser(),
            name = "Les Restos du Coeur",
            identifier = "W123456789",
            widgetToken = "clk_x",
        )
        every { associationRepo.findById(associationId) } returns Optional.of(association)
        every { associationRepo.save(any()) } answers { firstArg() }
        every { campaignRepo.countByAssociationIdAndStatus(associationId, CampaignStatus.LIVE) } returns 2
        every { donationRepo.countPendingByAssociationIdAndProviderRefPrefixSince(associationId, any(), any()) } returns 1
        val mollie = mockk<PaymentProvider> {
            every { readiness(associationId) } returns ProviderReadiness(ProviderReadinessStatus.READY)
        }
        every { registry.find(PaymentProviderId.MOLLIE) } returns mollie
        every { registry.find(PaymentProviderId.HELLOASSO) } returns null
    }

    @Test
    fun `preview lists every provider, null readiness when not implemented, and the change impact`() {
        val preview = service.preview(associationId)

        assertEquals(PaymentProviderId.MOLLIE, preview.currentProvider)
        assertEquals(ProviderReadinessStatus.READY, preview.providers.single { it.provider == PaymentProviderId.MOLLIE }.readiness)
        assertNull(preview.providers.single { it.provider == PaymentProviderId.HELLOASSO }.readiness)
        assertEquals(2, preview.liveCampaigns)
        assertEquals(true, preview.widgetEnabled)
        assertEquals(1, preview.pendingDonations)
        verify { donationRepo.countPendingByAssociationIdAndProviderRefPrefixSince(associationId, "mollie:", any()) }
    }

    @Test
    fun `preview of an unknown association is a 404`() {
        every { associationRepo.findById(associationId) } returns Optional.empty()

        assertThrows<NotFoundException> { service.preview(associationId) }
    }

    @Test
    fun `change journals from, to, trimmed reason and curator, then publishes the event`() {
        val payload = slot<Any>()

        val preview = service.change(associationId, PaymentProviderId.HELLOASSO, "  asked by the board  ", curatorId)

        assertEquals(PaymentProviderId.HELLOASSO, association.paymentProvider)
        assertEquals(PaymentProviderId.HELLOASSO, preview.currentProvider)
        verify { associationRepo.save(association) }
        verify {
            auditLog.append(
                ComplianceAuditLogService.PAYMENT_PROVIDER_CHANGED,
                ComplianceAuditSubjectType.ASSOCIATION,
                capture(payload),
                associationId,
                curatorId,
            )
        }
        assertEquals(mapOf("from" to "MOLLIE", "to" to "HELLOASSO", "reason" to "asked by the board"), payload.captured)
        verify { events.publishEvent(PaymentProviderChangedEvent(associationId, PaymentProviderId.MOLLIE, PaymentProviderId.HELLOASSO)) }
    }

    @Test
    fun `change to the current provider is a no-op - no save, no journal, no event`() {
        service.change(associationId, PaymentProviderId.MOLLIE, "nothing to do", curatorId)

        verify(exactly = 0) { associationRepo.save(any()) }
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { events.publishEvent(any<Any>()) }
    }

    @Test
    fun `change without a reason is refused before anything is read or written`() {
        assertThrows<UnprocessableEntityException> { service.change(associationId, PaymentProviderId.HELLOASSO, "   ", curatorId) }

        verify(exactly = 0) { associationRepo.findById(any()) }
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
        assertEquals(PaymentProviderId.MOLLIE, association.paymentProvider)
    }

    @Test
    fun `change with a reason over the maximum length is refused`() {
        val reason = "x".repeat(PaymentProviderChangeService.REASON_MAX_LENGTH + 1)

        assertThrows<UnprocessableEntityException> { service.change(associationId, PaymentProviderId.HELLOASSO, reason, curatorId) }
        assertFalse(association.paymentProvider == PaymentProviderId.HELLOASSO)
    }
}

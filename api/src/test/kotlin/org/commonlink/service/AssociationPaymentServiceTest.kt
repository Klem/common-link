package org.commonlink.service

import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.mockk
import org.commonlink.dto.HelloAssoLinkState
import org.commonlink.dto.HelloAssoStatusDto
import org.commonlink.dto.PaymentStatusDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.exception.UserNotFoundException
import org.commonlink.payment.BankSetupStatus
import org.commonlink.payment.HelloAssoWebhookState
import org.commonlink.payment.PaymentProvider
import org.commonlink.payment.PaymentProviderId
import org.commonlink.payment.PaymentProviderRegistry
import org.commonlink.payment.PaymentStatus
import org.commonlink.repository.AssociationProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

/** Pure unit tests of [AssociationPaymentService]. */
class AssociationPaymentServiceTest {

    private val associationProfileRepository = mockk<AssociationProfileRepository>()
    private val registry = mockk<PaymentProviderRegistry>()
    private val helloAssoStatusReader = mockk<HelloAssoStatusReader>()
    private val service = AssociationPaymentService(associationProfileRepository, registry, helloAssoStatusReader)

    private val userId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()

    @Test
    fun `returns the status computed by the association's provider, tagged with that provider`() {
        val association = mockk<AssociationProfile> { every { id } returns associationId }
        val provider = mockk<PaymentProvider> {
            every { id } returns PaymentProviderId.MOLLIE
            every { refreshStatusIfStale(associationId) } just runs
            every { paymentStatus(associationId) } returns
                PaymentStatus(BankSetupStatus.IN_REVIEW, actionUrl = "https://provider.example/onboarding", warnings = listOf("w"))
        }
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association)
        every { registry.findForAssociation(association) } returns provider

        assertEquals(
            PaymentStatusDto(
                provider = PaymentProviderId.MOLLIE,
                status = BankSetupStatus.IN_REVIEW,
                actionUrl = "https://provider.example/onboarding",
                warnings = listOf("w"),
            ),
            service.getPaymentStatus(userId),
        )
    }

    @Test
    fun `a HELLOASSO provider adds the helloasso block, read from the database`() {
        val association = mockk<AssociationProfile> { every { id } returns associationId }
        val provider = mockk<PaymentProvider> {
            every { id } returns PaymentProviderId.HELLOASSO
            every { refreshStatusIfStale(associationId) } just runs
            every { paymentStatus(associationId) } returns PaymentStatus(BankSetupStatus.IN_REVIEW)
        }
        val block = HelloAssoStatusDto(link = HelloAssoLinkState.OK, webhook = HelloAssoWebhookState.UNKNOWN, organizationSlug = "techno-plus")
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association)
        every { registry.findForAssociation(association) } returns provider
        every { helloAssoStatusReader.read(associationId) } returns block

        assertEquals(block, service.getPaymentStatus(userId).helloasso)
    }

    @Test
    fun `refuses a user without association profile`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.empty()

        assertThrows<UserNotFoundException> { service.getPaymentStatus(userId) }
    }

    @Test
    fun `refreshes a stale status at the provider before reading it`() {
        val association = mockk<AssociationProfile> { every { id } returns associationId }
        val provider = mockk<PaymentProvider> {
            every { id } returns PaymentProviderId.MOLLIE
            every { refreshStatusIfStale(associationId) } just runs
            every { paymentStatus(associationId) } returns PaymentStatus(BankSetupStatus.COMPLETED)
        }
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association)
        every { registry.findForAssociation(association) } returns provider

        service.getPaymentStatus(userId)

        verifyOrder {
            provider.refreshStatusIfStale(associationId)
            provider.paymentStatus(associationId)
        }
    }

    @Test
    fun `reports NOT_CONNECTED for a provider with no implementation yet, never another provider's status`() {
        val association = mockk<AssociationProfile> {
            every { id } returns associationId
            every { paymentProvider } returns PaymentProviderId.HELLOASSO
        }
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association)
        every { registry.findForAssociation(association) } returns null

        assertEquals(
            PaymentStatusDto(
                provider = PaymentProviderId.HELLOASSO,
                status = BankSetupStatus.NOT_CONNECTED,
                actionUrl = null,
                warnings = emptyList(),
            ),
            service.getPaymentStatus(userId),
        )
        verify(exactly = 0) { registry.forAssociation(any()) }
    }
}

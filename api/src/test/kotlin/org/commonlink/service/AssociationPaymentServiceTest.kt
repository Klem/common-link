package org.commonlink.service

import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verifyOrder
import io.mockk.mockk
import org.commonlink.dto.PaymentStatusDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.exception.UserNotFoundException
import org.commonlink.payment.BankSetupStatus
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
    private val service = AssociationPaymentService(associationProfileRepository, registry)

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
        every { registry.forAssociation(association) } returns provider

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
        every { registry.forAssociation(association) } returns provider

        service.getPaymentStatus(userId)

        verifyOrder {
            provider.refreshStatusIfStale(associationId)
            provider.paymentStatus(associationId)
        }
    }
}

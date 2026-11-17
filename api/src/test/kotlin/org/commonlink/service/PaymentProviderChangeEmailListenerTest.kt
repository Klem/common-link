package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.event.PaymentProviderChangedEvent
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.Optional
import java.util.UUID

/** Unit tests for [PaymentProviderChangeEmailListener]. Runs without Spring context. */
class PaymentProviderChangeEmailListenerTest {

    private val emailService: EmailService = mockk(relaxed = true)
    private val associationRepo: AssociationProfileRepository = mockk()
    private val listener = PaymentProviderChangeEmailListener(emailService, associationRepo, "https://app.common-link.test")

    private val associationId: UUID = UUID.randomUUID()
    private val profile: AssociationProfile = mockk()
    private val event = PaymentProviderChangedEvent(associationId, PaymentProviderId.MOLLIE, PaymentProviderId.HELLOASSO)

    @BeforeEach
    fun setUp() {
        every { profile.name } returns "Les Restos du Coeur"
        every { profile.contactEmail } returns "contact@restos.org"
        every { associationRepo.findById(associationId) } returns Optional.of(profile)
    }

    @Test
    fun `sends the change email with the new provider's name and the collection tab link`() {
        listener.onProviderChanged(event)

        verify(exactly = 1) {
            emailService.sendPaymentProviderChanged(
                "Les Restos du Coeur",
                "contact@restos.org",
                "HelloAsso",
                "https://app.common-link.test/fr/dashboard/association/profile?tab=bank",
            )
        }
    }

    @Test
    fun `no contact email - nothing sent`() {
        every { profile.contactEmail } returns "  "

        listener.onProviderChanged(event)

        verify(exactly = 0) { emailService.sendPaymentProviderChanged(any(), any(), any(), any()) }
    }

    @Test
    fun `unknown association - nothing sent`() {
        every { associationRepo.findById(associationId) } returns Optional.empty()

        listener.onProviderChanged(event)

        verify(exactly = 0) { emailService.sendPaymentProviderChanged(any(), any(), any(), any()) }
    }

    @Test
    fun `a sending failure is swallowed`() {
        every { emailService.sendPaymentProviderChanged(any(), any(), any(), any()) } throws RuntimeException("SMTP down")

        assertDoesNotThrow { listener.onProviderChanged(event) }
    }
}

package org.commonlink.payment

import io.mockk.every
import io.mockk.mockk
import org.commonlink.entity.AssociationProfile
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Pure unit tests of [PaymentProviderRegistry] resolution rules. */
class PaymentProviderRegistryTest {

    private fun provider(id: PaymentProviderId): PaymentProvider =
        mockk { every { this@mockk.id } returns id }

    private val mollie = provider(PaymentProviderId.MOLLIE)
    private val association = mockk<AssociationProfile>()

    @Test
    fun `forProviderRef resolves a mollie reference to the Mollie provider`() {
        val registry = PaymentProviderRegistry(listOf(mollie))

        assertSame(mollie, registry.forProviderRef("mollie:tr_x"))
    }

    @Test
    fun `forProviderRef returns null for a known prefix whose provider is not registered`() {
        val registry = PaymentProviderRegistry(listOf(mollie))

        assertNull(registry.forProviderRef("helloasso:1"))
    }

    @Test
    fun `forProviderRef returns null for an unknown prefix`() {
        val registry = PaymentProviderRegistry(listOf(mollie))

        assertNull(registry.forProviderRef("stripe:pi_x"))
    }

    @Test
    fun `forProviderRef resolves a helloasso reference once its provider is registered`() {
        val helloAsso = provider(PaymentProviderId.HELLOASSO)
        val registry = PaymentProviderRegistry(listOf(mollie, helloAsso))

        assertSame(helloAsso, registry.forProviderRef("helloasso:1"))
    }

    @Test
    fun `forAssociation returns the Mollie provider`() {
        val registry = PaymentProviderRegistry(listOf(mollie, provider(PaymentProviderId.HELLOASSO)))

        assertSame(mollie, registry.forAssociation(association))
    }

    @Test
    fun `forAssociation fails when no Mollie provider is registered`() {
        val registry = PaymentProviderRegistry(emptyList())

        assertThrows<IllegalStateException> { registry.forAssociation(association) }
    }

    @Test
    fun `construction is refused when two providers share an id`() {
        assertThrows<IllegalArgumentException> {
            PaymentProviderRegistry(listOf(mollie, provider(PaymentProviderId.MOLLIE)))
        }
    }
}

package org.commonlink.payment

import io.mockk.every
import io.mockk.mockk
import org.commonlink.entity.AssociationProfile
import org.commonlink.exception.ConflictException
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Pure unit tests of [PaymentProviderRegistry] resolution rules. */
class PaymentProviderRegistryTest {

    private fun provider(id: PaymentProviderId): PaymentProvider =
        mockk { every { this@mockk.id } returns id }

    private val mollie = provider(PaymentProviderId.MOLLIE)
    private fun association(provider: PaymentProviderId): AssociationProfile = mockk {
        every { paymentProvider } returns provider
        every { id } returns null
    }

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
    fun `forAssociation returns the Mollie provider for a MOLLIE association`() {
        val registry = PaymentProviderRegistry(listOf(mollie, provider(PaymentProviderId.HELLOASSO)))

        assertSame(mollie, registry.forAssociation(association(PaymentProviderId.MOLLIE)))
    }

    @Test
    fun `forAssociation returns the HelloAsso provider for a HELLOASSO association once registered`() {
        val helloAsso = provider(PaymentProviderId.HELLOASSO)
        val registry = PaymentProviderRegistry(listOf(mollie, helloAsso))

        assertSame(helloAsso, registry.forAssociation(association(PaymentProviderId.HELLOASSO)))
    }

    @Test
    fun `forAssociation refuses a HELLOASSO association without provider, never falling back to Mollie`() {
        val registry = PaymentProviderRegistry(listOf(mollie))

        assertThrows<ConflictException> { registry.forAssociation(association(PaymentProviderId.HELLOASSO)) }
    }

    @Test
    fun `findForAssociation returns null for a HELLOASSO association without provider`() {
        val registry = PaymentProviderRegistry(listOf(mollie))

        assertNull(registry.findForAssociation(association(PaymentProviderId.HELLOASSO)))
    }

    @Test
    fun `findForAssociation returns the provider of the association`() {
        val registry = PaymentProviderRegistry(listOf(mollie))

        assertSame(mollie, registry.findForAssociation(association(PaymentProviderId.MOLLIE)))
    }

    @Test
    fun `an in-flight mollie payment of an association moved to HELLOASSO is still resolved to Mollie by its prefix`() {
        val registry = PaymentProviderRegistry(listOf(mollie))

        assertSame(mollie, registry.forProviderRef("mollie:tr_x"))
    }

    @Test
    fun `construction is refused when two providers share an id`() {
        assertThrows<IllegalArgumentException> {
            PaymentProviderRegistry(listOf(mollie, provider(PaymentProviderId.MOLLIE)))
        }
    }
}

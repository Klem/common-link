package org.commonlink.payment

import java.util.UUID

/**
 * Port between the donation domain and a payment service provider.
 *
 * Implementations delegate to provider-specific services; the donation domain
 * ([org.commonlink.service.DonationService], receipts, on-chain proof) never depends on a provider directly.
 * Resolve an implementation through [PaymentProviderRegistry].
 */
interface PaymentProvider {

    /** Provider implemented by this adapter. */
    val id: PaymentProviderId

    /**
     * Returns whether the association can collect donations through this provider.
     *
     * Contract: side-effect free, database reads only, **no network call**. It is meant to be called from
     * unauthenticated public reads (widget, landing page) on every page view; a provider-side probe never belongs
     * here.
     *
     * @param associationId the association to check.
     */
    fun readiness(associationId: UUID): ProviderReadiness

    /**
     * Returns the association's payment-setup status for display.
     *
     * Same contract as [readiness]: side-effect free, database reads only, **no network call** — it reflects the
     * last status stored for the association.
     *
     * @param associationId the association to check.
     */
    fun paymentStatus(associationId: UUID): PaymentStatus

    /**
     * Returns whether the association has started linking an account with this provider. While it has, the
     * association's contact name and email are locked, since the provider may have captured them.
     *
     * @param associationId the association to check.
     */
    fun isLinkStarted(associationId: UUID): Boolean

    /**
     * Creates a payment at the provider for a pending donation.
     *
     * @param request provider-neutral checkout input.
     * @return the prefixed provider reference and the page the donor must be redirected to.
     * @throws org.commonlink.exception.ConflictException when the association cannot collect right now.
     */
    fun createCheckout(request: CheckoutRequest): CheckoutSession

    /**
     * Re-reads a payment at the provider and confirms the donation if it is paid. Idempotent: an already confirmed
     * donation is left unchanged. Never trusts browser parameters or notification bodies.
     *
     * @param providerRef the donation's prefixed provider reference.
     */
    fun refreshPayment(providerRef: String)
}

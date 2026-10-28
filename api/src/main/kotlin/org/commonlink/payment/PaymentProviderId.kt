package org.commonlink.payment

/**
 * Identifies a payment service provider (PSP) that collects donations on an association's behalf.
 *
 * Every [org.commonlink.entity.Donation.providerRef] starts with the [refPrefix] of the provider that created it.
 * That prefix — never the association's current provider — decides which provider re-reads an in-flight payment
 * (see [PaymentProviderRegistry.forProviderRef]), so a donation started before a provider change is still settled
 * by the provider that took it.
 *
 * @property refPrefix prefix of every `providerRef` minted by this provider, colon included.
 */
enum class PaymentProviderId(val refPrefix: String) {
    /** Mollie Connect (OAuth-linked merchant account). */
    MOLLIE("mollie:"),

    /** HelloAsso Checkout with association API keys. No adapter exists yet. */
    HELLOASSO("helloasso:"),
}

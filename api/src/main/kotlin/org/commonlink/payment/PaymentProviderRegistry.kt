package org.commonlink.payment

import org.commonlink.entity.AssociationProfile
import org.springframework.stereotype.Service

/**
 * Resolves the [PaymentProvider] to use, either for an association (new payments, collection gates) or for an
 * existing payment reference (in-flight payments).
 *
 * @param providers every [PaymentProvider] bean; at most one per [PaymentProviderId].
 * @throws IllegalArgumentException at construction when two providers declare the same id.
 */
@Service
class PaymentProviderRegistry(providers: List<PaymentProvider>) {

    private val byId: Map<PaymentProviderId, PaymentProvider> = providers.associateBy { it.id }

    init {
        require(byId.size == providers.size) {
            "Duplicate payment providers: ${providers.groupBy { it.id }.filterValues { it.size > 1 }.keys}"
        }
    }

    /**
     * Returns the provider that handles new payments and collection gates for [association].
     *
     * Always [PaymentProviderId.MOLLIE] for now: the per-association provider (`association_profiles.payment_provider`)
     * is read from lot C onwards.
     *
     * @param association the association collecting donations.
     * @throws IllegalStateException when no Mollie provider is registered (wiring error).
     */
    fun forAssociation(association: AssociationProfile): PaymentProvider =
        byId[PaymentProviderId.MOLLIE]
            ?: throw IllegalStateException("No payment provider registered for ${PaymentProviderId.MOLLIE}")

    /**
     * Returns the provider that created [providerRef], read from its prefix — never from the association's current
     * provider, so a payment started before a provider change is still settled by the provider that took it.
     *
     * @param providerRef a donation's prefixed provider reference (e.g. `mollie:tr_xxx`).
     * @return the matching provider, or `null` when the prefix is unknown (legacy references) or its provider is not
     *   registered.
     */
    fun forProviderRef(providerRef: String): PaymentProvider? =
        PaymentProviderId.entries
            .firstOrNull { providerRef.startsWith(it.refPrefix) }
            ?.let { byId[it] }
}

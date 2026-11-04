package org.commonlink.payment

import org.commonlink.entity.AssociationProfile
import org.commonlink.exception.ConflictException
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(javaClass)

    private val byId: Map<PaymentProviderId, PaymentProvider> = providers.associateBy { it.id }

    init {
        require(byId.size == providers.size) {
            "Duplicate payment providers: ${providers.groupBy { it.id }.filterValues { it.size > 1 }.keys}"
        }
    }

    /**
     * Returns the provider of [association] ([AssociationProfile.paymentProvider]), or `null` when that provider has
     * no registered implementation (defensive; every provider has one today). Never falls back to another provider.
     *
     * For read paths and collection gates: a `null` result means "not ready to collect" (status `NOT_CONNECTED`,
     * no link started, every gate closed).
     *
     * @param association the association collecting donations.
     */
    fun findForAssociation(association: AssociationProfile): PaymentProvider? = find(association.paymentProvider)

    /**
     * Returns the implementation of provider [id], or `null` when it has no registered implementation (defensive;
     * every provider has one today). For displays that list every provider, e.g. the curator's provider-change preview.
     */
    fun find(id: PaymentProviderId): PaymentProvider? = byId[id]

    /**
     * Returns the provider that takes new payments for [association] ([AssociationProfile.paymentProvider]).
     * Never falls back to another provider.
     *
     * @param association the association collecting donations.
     * @throws ConflictException when the association's provider has no registered implementation (defensive; every
     *   provider has one today).
     */
    fun forAssociation(association: AssociationProfile): PaymentProvider =
        findForAssociation(association) ?: run {
            logger.warn("Payment provider {} unavailable for association {}", association.paymentProvider, association.id)
            throw ConflictException("Payment provider ${association.paymentProvider} is not available yet")
        }

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

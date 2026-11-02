package org.commonlink.config

import org.commonlink.payment.PaymentProviderId
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration of the payment service providers that collect donations.
 *
 * **Default provider** — [defaultProvider] is written to
 * [org.commonlink.entity.AssociationProfile.paymentProvider] when an association is created, and never read
 * again afterwards: changing it only affects associations created from then on. New associations go to HelloAsso;
 * Mollie is set by hand by the curator, on an association's specific request. Associations created before the
 * HelloAsso support stay on Mollie (migration V87).
 *
 * @property defaultProvider Payment provider given to a newly created association.
 */
@ConfigurationProperties(prefix = "app.payments")
data class PaymentsProperties(
    val defaultProvider: PaymentProviderId = PaymentProviderId.HELLOASSO,
)

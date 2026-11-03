package org.commonlink.payment

import java.math.BigDecimal
import java.util.UUID

/**
 * Provider-neutral input of a donation checkout, built by the public donation flow once the cap, the guest donor
 * and the asset-freeze screening have passed.
 *
 * It carries raw ingredients rather than ready-made URLs or payloads: each provider builds its own return URLs,
 * description, metadata and idempotency key from them, because their formats differ (field lengths, error URL,
 * metadata keys). The currency is always EUR.
 *
 * @property associationId the collecting association.
 * @property campaignId the campaign receiving the donation.
 * @property campaignName campaign name, used by providers to label the payment.
 * @property donorProfileId the (guest) donor profile.
 * @property amount donation amount in EUR.
 * @property publicRef opaque reference minted for this donation; the only id the donor-facing return page polls by.
 *   Unique per call, so providers may also use it as their idempotency key.
 * @property widgetToken token of the widget the donation comes from, part of the return URL path.
 * @property locale two-letter UI locale, already validated by the caller.
 * @property sourceSite page that embedded the widget, already sanitized but not URL-encoded; `null` when unknown.
 * @property payer donor identity a provider may use to pre-fill its payment page (HelloAsso); ignored by Mollie.
 */
data class CheckoutRequest(
    val associationId: UUID,
    val campaignId: UUID,
    val campaignName: String,
    val donorProfileId: UUID,
    val amount: BigDecimal,
    val publicRef: UUID,
    val widgetToken: String,
    val locale: String,
    val sourceSite: String?,
    val payer: CheckoutPayer? = null,
)

/**
 * Donor identity as entered in the donation form, for providers that pre-fill their payment page. Raw values: each
 * provider keeps only what its own rules accept. Never a birth date.
 *
 * @property country ISO 3166-1 alpha-2 code.
 */
data class CheckoutPayer(
    val email: String?,
    val fullName: String?,
    val addressLine1: String?,
    val postalCode: String?,
    val city: String?,
    val country: String?,
)

/**
 * Result of a checkout created at the provider.
 *
 * @property providerRef reference stored on the pending donation, already prefixed with
 *   [PaymentProviderId.refPrefix] (e.g. `mollie:tr_xxx`).
 * @property redirectUrl provider-hosted payment page the donor is sent to. Never null: a provider that returns no
 *   payment page fails the checkout before any donation is recorded.
 * @property providerPaymentId the provider's own payment id, unprefixed.
 */
data class CheckoutSession(
    val providerRef: String,
    val redirectUrl: String,
    val providerPaymentId: String,
)

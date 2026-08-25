package org.commonlink.dto

import java.util.UUID

/**
 * Response for POST /api/public/widget/{widgetToken}/donations.
 *
 * @param checkoutUrl Mollie hosted-checkout URL — redirect the donor here to complete the payment.
 * @param paymentId Mollie payment identifier (e.g. tr_xxx). Informational only — the return page
 *                  never needs it, since it never sees the browser again before the Mollie round
 *                  trip; use [publicRef] for anything that must survive that trip.
 * @param publicRef Opaque correlation id also embedded in the Mollie redirect URL (`ref` query
 *                  param). Use it as the GA4 `transaction_id` on the `begin_checkout` push made
 *                  before redirecting, so it matches the `purchase` push made on the return page.
 */
data class CreateGuestDonationResponse(
    val checkoutUrl: String,
    val paymentId: String,
    val publicRef: UUID,
)

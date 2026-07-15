package org.commonlink.dto

/**
 * Response for POST /api/public/widget/{widgetToken}/donations.
 *
 * @param checkoutUrl Mollie hosted-checkout URL — redirect the donor here to complete the payment.
 * @param paymentId Mollie payment identifier (e.g. tr_xxx). Store client-side before redirecting
 *                  so the return page can poll the donation status endpoint.
 */
data class CreateGuestDonationResponse(
    val checkoutUrl: String,
    val paymentId: String,
)

package org.commonlink.dto

import jakarta.validation.constraints.Size

/**
 * Request body for PATCH /api/association/me/widget.
 *
 * Configures the Mollie redirect URLs for the association's donation widget.
 * Null fields clear the corresponding value (reverts to server-configured fallback).
 */
data class UpdateWidgetConfigRequest(

    /**
     * URL of the association's thank-you page.
     * Mollie redirects donors here after successful payment.
     * Must be a valid http/https URL. Null clears the field.
     */
    @field:Size(max = 512, message = "must not exceed 512 characters")
    val widgetRedirectUrl: String?,

    /**
     * URL to return donors to on payment cancellation.
     * Must be a valid http/https URL. Null clears the field.
     */
    @field:Size(max = 512, message = "must not exceed 512 characters")
    val widgetCancelUrl: String?,
)

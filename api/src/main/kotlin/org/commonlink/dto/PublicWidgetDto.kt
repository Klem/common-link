package org.commonlink.dto

import java.math.BigDecimal
import java.util.UUID

/**
 * Public projection returned by the widget endpoint.
 *
 * Intentionally minimal — exposes only what the donation iframe needs.
 * No internal IDs (association, user), no contact details, no verification status.
 */
data class PublicWidgetDto(
    val associationName: String,
    val campaignId: UUID,
    val campaignName: String,
    val campaignEmoji: String,
    val campaignDescription: String?,
    val goal: BigDecimal,
    val raised: BigDecimal,
    val campaignCoverImage: String?,
    val currency: String = "EUR",
    /** Allowed origin for widget post-payment redirects. Null if not configured. */
    val widgetAllowedOrigin: String? = null,
    /**
     * Amount the campaign may still accept, from
     * [org.commonlink.service.DonationCapService.remainingCapacity].
     *
     * Lets the form cap the amount input instead of letting the donor fill everything in and be
     * refused on submit. The backend stays the authority — every click is replayable (rule 8).
     * Zero means the campaign is full.
     *
     * Deliberately without a default: a forgotten call site would otherwise serialise `0` and render
     * every widget as full.
     */
    val remainingCapacity: BigDecimal,
)

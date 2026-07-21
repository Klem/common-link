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
)

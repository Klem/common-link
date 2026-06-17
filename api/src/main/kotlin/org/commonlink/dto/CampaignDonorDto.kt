package org.commonlink.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Aggregated view of a donor's contributions to a single campaign.
 *
 * [displayName] is already masked by the service: anonymous donors are presented as "Anonyme".
 * [donorId] is always present (UUID is not PII) to support detail deep-links from the frontend.
 */
data class CampaignDonorDto(
    val donorId: UUID,
    val displayName: String,
    val totalAmount: BigDecimal,
    val txCount: Long,
    val lastDonationAt: Instant?,
)

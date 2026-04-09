package org.commonlink.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Request body for creating a new campaign under the authenticated association.
 *
 * Every constraint enforced in the frontend must also be enforced here —
 * every click can be replayed as a direct API call, so server-side validation is mandatory.
 *
 * @param name Display name of the campaign (required, max 255 characters).
 * @param emoji Visual icon emoji (max 10 characters). Defaults to "🌍" if null.
 * @param description Optional detailed description of the campaign.
 * @param goal Total fundraising goal in euros. Must be >= 0. Defaults to 0 if null.
 * @param startDate Optional date when the campaign starts accepting donations.
 * @param endDate Optional date when the campaign stops accepting donations.
 */
data class CreateCampaignRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:Size(max = 10)
    val emoji: String? = null,

    val description: String? = null,

    @field:DecimalMin("0")
    val goal: BigDecimal? = null,

    val startDate: LocalDate? = null,

    val endDate: LocalDate? = null
)

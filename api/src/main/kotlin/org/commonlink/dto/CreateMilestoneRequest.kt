package org.commonlink.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * Request body for adding a new milestone to a campaign.
 *
 * Every constraint enforced in the frontend must also be enforced here —
 * every click can be replayed as a direct API call, so server-side validation is mandatory.
 *
 * @param title Short title describing what will be achieved at this milestone (required, max 255 characters).
 * @param emoji Visual icon emoji (max 10 characters). Defaults to "🎯" if null.
 * @param description Optional detailed description of the milestone deliverable.
 * @param targetAmount Amount of donations required to reach this milestone (>= 0). Defaults to 0.
 * @param sortOrder Display position in the milestone list; lower value = shown first.
 */
data class CreateMilestoneRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    @field:Size(max = 10)
    val emoji: String? = null,

    val description: String? = null,

    @field:DecimalMin("0")
    val targetAmount: BigDecimal = BigDecimal.ZERO,

    val sortOrder: Int = 0
)

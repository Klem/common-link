package org.commonlink.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import org.commonlink.entity.CampaignStatus
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Request body for partially updating an existing campaign.
 *
 * Uses the partial-update pattern: null fields are ignored and the corresponding
 * campaign field is left unchanged. Only non-null fields are applied.
 *
 * The backend enforces valid status transitions (DRAFT → LIVE → ENDED only —
 * no going backwards; ENDED is terminal).
 *
 * @param name New display name (max 255 characters). Null = no change.
 * @param emoji New visual icon emoji (max 10 characters). Null = no change.
 * @param description New description text. Null = no change.
 * @param goal New fundraising goal in euros (>= 0). Null = no change.
 * @param status New lifecycle status. Must follow valid transition rules. Null = no change.
 * @param startDate New start date. Null = no change.
 * @param endDate New end date. Null = no change.
 * @param contractAddress On-chain contract address after deployment. Null = no change.
 */
data class UpdateCampaignRequest(
    @field:Size(max = 255)
    val name: String? = null,

    @field:Size(max = 10)
    val emoji: String? = null,

    val description: String? = null,

    @field:DecimalMin("0")
    val goal: BigDecimal? = null,

    val status: CampaignStatus? = null,

    val startDate: LocalDate? = null,

    val endDate: LocalDate? = null,

    val contractAddress: String? = null
)

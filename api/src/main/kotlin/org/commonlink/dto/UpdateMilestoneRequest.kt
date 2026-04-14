package org.commonlink.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import org.commonlink.entity.MilestoneStatus
import java.math.BigDecimal

/**
 * Request body for partially updating an existing campaign milestone.
 *
 * Uses the partial-update pattern: null fields are ignored and the corresponding
 * milestone field is left unchanged. Only non-null fields are applied.
 *
 * @param title New title (max 255 characters). Null = no change.
 * @param emoji New visual icon emoji (max 10 characters). Null = no change.
 * @param description New description text. Null = no change.
 * @param targetAmount New target amount in euros (>= 0). Null = no change.
 * @param status New milestone status. Null = no change.
 * @param sortOrder New display position. Null = no change.
 */
data class UpdateMilestoneRequest(
    @field:Size(max = 255)
    val title: String? = null,

    @field:Size(max = 10)
    val emoji: String? = null,

    val description: String? = null,

    @field:DecimalMin("0")
    val targetAmount: BigDecimal? = null,

    val status: MilestoneStatus? = null,

    val sortOrder: Int? = null
)

package org.commonlink.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.commonlink.entity.BudgetSide
import java.math.BigDecimal

/**
 * Request body for bulk-replacing the entire budget of a campaign.
 *
 * The complete budget structure is sent as one payload. The backend deletes all existing
 * sections and items for the campaign, then recreates them from this request.
 * Sending an empty [sections] list clears the budget entirely.
 *
 * @param sections Ordered list of budget sections to create (replaces existing budget).
 */
data class SaveBudgetRequest(
    @field:Valid
    val sections: List<SaveBudgetSectionRequest>
)

/**
 * A single budget section within a [SaveBudgetRequest].
 *
 * @param side Whether this section is EXPENSE or REVENUE.
 * @param code Short unique code identifying the section (max 50 characters).
 * @param name Human-readable label (max 255 characters).
 * @param sortOrder Display position; lower value = shown first.
 * @param items Ordered list of line items in this section.
 */
data class SaveBudgetSectionRequest(
    @field:NotNull
    val side: BudgetSide,

    @field:NotBlank
    @field:Size(max = 50)
    val code: String,

    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    val sortOrder: Int = 0,

    @field:Valid
    val items: List<SaveBudgetItemRequest> = emptyList()
)

/**
 * A single line item within a [SaveBudgetSectionRequest].
 *
 * @param label Description of this budget line (max 255 characters).
 * @param amount Estimated amount in euros (>= 0).
 * @param sortOrder Display position within the section; lower value = shown first.
 */
data class SaveBudgetItemRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val label: String,

    @field:DecimalMin("0")
    val amount: BigDecimal = BigDecimal.ZERO,

    val sortOrder: Int = 0
)

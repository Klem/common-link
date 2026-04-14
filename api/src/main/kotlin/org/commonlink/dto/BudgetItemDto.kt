package org.commonlink.dto

import org.commonlink.entity.CampaignBudgetItem
import java.math.BigDecimal
import java.util.UUID

/**
 * Response DTO for a single budget line item.
 *
 * @param id UUID of the budget item.
 * @param label Description of this budget line (e.g. "Impression flyers").
 * @param amount Estimated amount in euros.
 * @param sortOrder Display position within the section; lower value = shown first.
 */
data class BudgetItemDto(
    val id: UUID,
    val label: String,
    val amount: BigDecimal,
    val sortOrder: Int
)

/**
 * Converts a [CampaignBudgetItem] entity to a [BudgetItemDto].
 */
fun CampaignBudgetItem.toDto() = BudgetItemDto(
    id = id!!,
    label = label,
    amount = amount,
    sortOrder = sortOrder
)

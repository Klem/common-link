package org.commonlink.dto

import org.commonlink.entity.BudgetSide
import org.commonlink.entity.CampaignBudgetSection
import java.util.UUID

/**
 * Response DTO for a budget section, including its ordered line items.
 *
 * @param id UUID of the budget section.
 * @param side Whether this section is EXPENSE or REVENUE.
 * @param code Short unique code identifying the section (e.g. "CHARGES_EXT").
 * @param name Human-readable label displayed in the budget UI.
 * @param sortOrder Display position; lower value = shown first.
 * @param items Ordered list of line items belonging to this section.
 */
data class BudgetSectionDto(
    val id: UUID,
    val side: BudgetSide,
    val code: String,
    val name: String,
    val sortOrder: Int,
    val items: List<BudgetItemDto>
)

/**
 * Converts a [CampaignBudgetSection] entity to a [BudgetSectionDto], sorting items by [sortOrder].
 */
fun CampaignBudgetSection.toDto() = BudgetSectionDto(
    id = id!!,
    side = side,
    code = code,
    name = name,
    sortOrder = sortOrder,
    items = items.sortedBy { it.sortOrder }.map { it.toDto() }
)

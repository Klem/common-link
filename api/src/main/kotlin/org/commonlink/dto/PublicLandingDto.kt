package org.commonlink.dto

import org.commonlink.entity.BudgetSide
import org.commonlink.entity.CampaignBudgetSection
import org.commonlink.entity.LandingTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Public landing page projection for an association's active widget campaign.
 *
 * Only exposes donor-safe data — no internal IDs, no contact details, no sensitive fields.
 *
 * @param associationName Legal name of the association.
 * @param associationRna RNA or SIREN identifier from [org.commonlink.entity.AssociationProfile.identifier].
 * @param taxReductionRate Applicable fiscal reduction rate (66 or 75) per [org.commonlink.service.TaxRateService].
 * @param budget EXPENSE budget items with percentages, sorted by amount descending. Empty when total is zero.
 * @param budgetHash Integrity hash of the published budget, null when no budget has been published.
 * @param landingTheme Visual palette chosen by the association. Drives the `--lp-*` token overrides.
 * @param landingLogo Public serving path of the association logo, null when none was uploaded.
 * @param showProject Whether the "what this donation funds" section must be rendered.
 * @param showTransparency Whether the budget / milestones section must be rendered.
 * @param showTrust Whether the "donate with confidence" section must be rendered.
 */
data class PublicLandingDto(
    val associationName: String,
    val associationRna: String,
    val addressLine1: String?,
    val city: String?,
    val postalCode: String?,
    val legalObject: String?,
    val creationYear: Short?,
    val taxReductionRate: Int,
    val campaignId: UUID,
    val campaignName: String,
    val campaignEmoji: String,
    val campaignDescription: String?,
    val campaignReason: String?,
    val campaignImpactGoals: String?,
    val campaignCategory: String?,
    val goal: BigDecimal,
    val raised: BigDecimal,
    val currency: String = "EUR",
    val coverImage: String?,
    val budget: List<LandingBudgetPostDto>,
    val budgetHash: String?,
    val milestones: List<MilestoneDto>,
    val widgetAllowedOrigin: String? = null,
    val landingTheme: LandingTheme = LandingTheme.DEFAULT,
    val landingLogo: String? = null,
    val showProject: Boolean = true,
    val showTransparency: Boolean = true,
    val showTrust: Boolean = true,
)

/**
 * A single EXPENSE budget item with its share of the total budget.
 *
 * @param percentage Rounded integer percentage of this item relative to total EXPENSE (HALF_UP).
 */
data class LandingBudgetPostDto(
    val label: String,
    val amount: BigDecimal,
    val percentage: Int,
)

/**
 * Projects EXPENSE budget items as a sorted list with per-item percentage.
 *
 * Filters to [BudgetSide.EXPENSE] sections only, flattens their items, computes
 * per-item percentage (HALF_UP), and sorts by amount descending.
 * Returns an empty list when the total is zero.
 */
internal fun buildBudgetProjection(sections: List<CampaignBudgetSection>): List<LandingBudgetPostDto> {
    val items = sections
        .filter { it.side == BudgetSide.EXPENSE }
        .flatMap { it.items }
    val total = items.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
    if (total.signum() <= 0) return emptyList()
    return items
        .map { item ->
            LandingBudgetPostDto(
                label = item.label,
                amount = item.amount,
                percentage = item.amount.multiply(BigDecimal(100))
                    .divide(total, 0, RoundingMode.HALF_UP)
                    .toInt(),
            )
        }
        .sortedByDescending { it.amount }
}

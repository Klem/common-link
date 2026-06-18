package org.commonlink.dto

import java.math.BigDecimal

/**
 * Budget variance report for a single campaign: planned vs actual per budget section.
 *
 * @param charges Expense sections with planned vs actual amounts.
 * @param produits Revenue sections with planned vs actual amounts.
 * @param totals Aggregate totals across all sections.
 */
data class BudgetVarianceDto(
    val charges: List<SectionVarianceDto>,
    val produits: List<SectionVarianceDto>,
    val totals: TotalsVarianceDto,
)

/**
 * Variance for one budget section.
 *
 * @param sectionCode Section code (e.g. "60", "74").
 * @param sectionName User-defined section name.
 * @param planned Sum of all budget items in this section (euros).
 * @param actual Realised amount: confirmed payouts (charges) or confirmed donations (produits) matching this section code.
 * @param variance actual - planned; negative means under-spend (charges) or shortfall (produits).
 */
data class SectionVarianceDto(
    val sectionCode: String,
    val sectionName: String,
    val planned: BigDecimal,
    val actual: BigDecimal,
    val variance: BigDecimal,
)

/**
 * Campaign-level totals across all budget sections.
 */
data class TotalsVarianceDto(
    val totalPlannedCharges: BigDecimal,
    val totalActualCharges: BigDecimal,
    val totalPlannedProduits: BigDecimal,
    val totalActualProduits: BigDecimal,
)

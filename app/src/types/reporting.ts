/** Variance for one budget section (charges or produits). */
export interface SectionVariance {
  sectionCode: string;
  sectionName: string;
  /** Sum of all budget items in this section (euros). */
  planned: number;
  /** Realised amount: confirmed payouts (charges) or donations (produits). */
  actual: number;
  /** actual − planned; negative = under-spend (charges) or shortfall (produits). */
  variance: number;
}

/** Campaign-level totals across all budget sections. */
export interface TotalsVariance {
  totalPlannedCharges: number;
  totalActualCharges: number;
  totalPlannedProduits: number;
  totalActualProduits: number;
}

/** Full budget variance report for a single campaign. */
export interface BudgetVariance {
  charges: SectionVariance[];
  produits: SectionVariance[];
  totals: TotalsVariance;
}

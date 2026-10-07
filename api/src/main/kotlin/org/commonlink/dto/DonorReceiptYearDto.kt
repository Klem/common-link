package org.commonlink.dto

import java.math.BigDecimal

/** One year's fiscal recap -- feeds the "Reçus fiscaux" tab's list of annual summaries. */
data class DonorReceiptYearDto(
    val year: Int,
    val donationCount: Int,
    val totalAmount: BigDecimal,
    /** Sum of line-level `amount x rate in force at donation date` -- see [org.commonlink.service.TaxRateService]. */
    val estimatedDeduction: BigDecimal,
)

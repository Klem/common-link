package org.commonlink.dto

import java.math.BigDecimal
import java.time.Instant

/** Content of one donor's annual fiscal recap PDF for [year] -- see [AnnualReceiptLineDto]. */
data class AnnualReceiptsSummaryDto(
    val year: Int,
    val lines: List<AnnualReceiptLineDto>,
    val totalAmount: BigDecimal,
    val totalDeduction: BigDecimal,
)

/** One receipted donation line of the annual recap, with the rate applied at donation time. */
data class AnnualReceiptLineDto(
    val associationName: String,
    val confirmedAt: Instant,
    val amount: BigDecimal,
    val receiptNumber: String,
    val rate: Int,
    val deductionAmount: BigDecimal,
)

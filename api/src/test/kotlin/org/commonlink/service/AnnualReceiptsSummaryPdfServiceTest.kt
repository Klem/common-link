package org.commonlink.service

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import org.commonlink.dto.AnnualReceiptLineDto
import org.commonlink.dto.AnnualReceiptsSummaryDto
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/** Asserts on the text actually rendered into the annual fiscal recap PDF, same style as ReceiptServiceTest. */
class AnnualReceiptsSummaryPdfServiceTest {

    private val service = AnnualReceiptsSummaryPdfService()

    private fun pdfText(summary: AnnualReceiptsSummaryDto): String {
        val bytes = service.generate(summary)
        val reader = PdfReader(bytes)
        val text = (1..reader.numberOfPages).joinToString(" ") { PdfTextExtractor(reader).getTextFromPage(it) }
        reader.close()
        return text
    }

    @Test
    fun `prints the year, association name and receipt number of each line`() {
        val summary = AnnualReceiptsSummaryDto(
            year = 2025,
            lines = listOf(
                AnnualReceiptLineDto(
                    associationName = "Les Restos Solidaires",
                    confirmedAt = Instant.parse("2025-06-01T10:00:00Z"),
                    amount = BigDecimal("100.00"),
                    receiptNumber = "REC-2025-0007",
                    rate = 66,
                    deductionAmount = BigDecimal("66.00"),
                )
            ),
            totalAmount = BigDecimal("100.00"),
            totalDeduction = BigDecimal("66.00"),
        )

        val text = pdfText(summary)

        assertTrue(text.contains("2025"), text)
        assertTrue(text.contains("Les Restos Solidaires"), text)
        assertTrue(text.contains("REC-2025-0007"), text)
    }

    @Test
    fun `prints the totals, not just per-line amounts`() {
        val summary = AnnualReceiptsSummaryDto(
            year = 2025,
            lines = listOf(
                AnnualReceiptLineDto(
                    associationName = "Asso", confirmedAt = Instant.parse("2025-06-01T10:00:00Z"),
                    amount = BigDecimal("100.00"), receiptNumber = "REC-1", rate = 66, deductionAmount = BigDecimal("66.00"),
                )
            ),
            totalAmount = BigDecimal("100.00"),
            totalDeduction = BigDecimal("65.99"),
        )

        val text = pdfText(summary)

        // The KPI total (65,99, rounded once across all lines) must appear even though the
        // single line above also shows 66,00 -- proves the PDF renders summary.totalDeduction
        // and does not recompute it by summing the already-rounded lines.
        assertTrue(text.contains("65,99"), text)
    }
}

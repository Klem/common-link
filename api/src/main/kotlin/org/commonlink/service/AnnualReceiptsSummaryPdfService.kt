package org.commonlink.service

import com.lowagie.text.Document
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.commonlink.dto.AnnualReceiptsSummaryDto
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders a donor's annual fiscal recap ([AnnualReceiptsSummaryDto]) as a downloadable PDF.
 *
 * Uses OpenPDF (`com.lowagie.text.*`) -- same library and the same 3-color palette as
 * [CampaignReportPdfService], the model this service follows. [ReceiptService] is not reused:
 * its methods are all private and render a single Cerfa receipt, a different document.
 */
@Service
class AnnualReceiptsSummaryPdfService {

    private val parisZone = ZoneId.of("Europe/Paris")
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(parisZone)

    private val TEAL = Color(6, 110, 145)
    private val LIGHT_GREY = Color(245, 247, 250)
    private val MID_GREY = Color(180, 180, 180)

    fun generate(summary: AnnualReceiptsSummaryDto): ByteArray {
        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 50f, 50f, 50f, 50f)
        PdfWriter.getInstance(document, baos)
        document.open()

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)
        val captionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f).apply { color = TEAL }
        val valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val bigFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)

        document.add(Paragraph("Récapitulatif fiscal ${summary.year}", titleFont).apply { spacingAfter = 10f })

        val kpiTable = PdfPTable(2)
        kpiTable.widthPercentage = 100f
        kpiTable.setSpacingAfter(10f)
        kpiTable.addCell(kpiCell("Total des dons reçus", "%,.2f €".format(summary.totalAmount), captionFont, bigFont))
        kpiTable.addCell(kpiCell("Réduction fiscale estimée", "%,.2f €".format(summary.totalDeduction), captionFont, bigFont))
        document.add(kpiTable)

        val table = PdfPTable(5)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(2f, 3f, 1.5f, 2f, 1.5f))
        listOf("Date", "Association", "Montant", "N° de reçu", "Réduction").forEach { header ->
            table.addCell(PdfPCell(Phrase(header, captionFont)).apply { backgroundColor = LIGHT_GREY; setPadding(4f) })
        }
        summary.lines.forEach { line ->
            table.addCell(PdfPCell(Phrase(dateFmt.format(line.confirmedAt), valueFont)).apply { setPadding(4f) })
            table.addCell(PdfPCell(Phrase(line.associationName, valueFont)).apply { setPadding(4f) })
            table.addCell(PdfPCell(Phrase("%,.2f €".format(line.amount), valueFont)).apply { setPadding(4f) })
            table.addCell(PdfPCell(Phrase(line.receiptNumber, valueFont)).apply { setPadding(4f) })
            val deductionText = "%,.2f €".format(line.deductionAmount) + " (${line.rate}%)"
            table.addCell(PdfPCell(Phrase(deductionText, valueFont)).apply { setPadding(4f) })
        }
        document.add(table)

        document.close()
        return baos.toByteArray()
    }

    private fun kpiCell(label: String, value: String, captionFont: com.lowagie.text.Font, valueFont: com.lowagie.text.Font): PdfPCell =
        PdfPCell().apply {
            backgroundColor = LIGHT_GREY
            border = com.lowagie.text.Rectangle.BOX
            borderColor = MID_GREY
            setPadding(8f)
            addElement(Paragraph(label, captionFont))
            addElement(Paragraph(value, valueFont))
        }
}

package org.commonlink.service

import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.commonlink.dto.DonorCampaignReportDto
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders the donor-facing "bilan de campagne" ([DonorCampaignReportDto]) as a downloadable PDF.
 *
 * Uses OpenPDF (`com.lowagie.text.*`) — the same library as [ReceiptService], no new dependency.
 * Content is minimal by design (spec §2.6): hero, contribution, KPIs, milestones, confirmed
 * payouts, and the validated registry banner text — never a fuller claim than the on-screen report.
 */
@Service
class CampaignReportPdfService {

    private val parisZone = ZoneId.of("Europe/Paris")
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(parisZone)

    private val TEAL = Color(6, 110, 145)
    private val LIGHT_GREY = Color(245, 247, 250)
    private val MID_GREY = Color(180, 180, 180)

    fun generate(report: DonorCampaignReportDto): ByteArray {
        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 50f, 50f, 50f, 50f)
        PdfWriter.getInstance(document, baos)
        document.open()

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)
        val captionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f).apply { color = TEAL }
        val valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val bigFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)
        val smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8f)

        document.add(Paragraph("${report.campaignEmoji} ${report.campaignName}", titleFont))
        document.add(Paragraph(report.associationName, valueFont).apply { spacingAfter = 10f })

        val kpiTable = PdfPTable(3)
        kpiTable.widthPercentage = 100f
        kpiTable.setSpacingAfter(10f)
        kpiTable.addCell(kpiCell("Collecté / objectif", "%,.2f € / %,.2f €".format(report.raised, report.goal), captionFont, bigFont))
        kpiTable.addCell(kpiCell("Votre contribution", "%,.2f €".format(report.donorContribution), captionFont, bigFont))
        val totalSpent = report.confirmedPayouts.sumOf { it.amount }
        kpiTable.addCell(kpiCell("Dépenses confirmées", "%,.2f €".format(totalSpent), captionFont, bigFont))
        document.add(kpiTable)

        if (report.milestones.isNotEmpty()) {
            document.add(Paragraph("Paliers", captionFont).apply { spacingAfter = 4f })
            report.milestones.sortedBy { it.sortOrder }.forEach {
                document.add(Paragraph("${it.emoji} ${it.title} — ${it.status}", valueFont))
            }
            document.add(Paragraph(" ", valueFont).apply { spacingAfter = 6f })
        }

        document.add(Paragraph("Utilisation des fonds — dépenses confirmées", captionFont).apply { spacingAfter = 4f })
        if (report.confirmedPayouts.isEmpty()) {
            document.add(Paragraph("Aucune dépense confirmée pour le moment.", valueFont))
        } else {
            val table = PdfPTable(3)
            table.widthPercentage = 100f
            table.setWidths(floatArrayOf(2f, 3f, 1.5f))
            table.setSpacingAfter(10f)
            listOf("Date", "Dépense", "Montant").forEach { header ->
                table.addCell(PdfPCell(Phrase(header, captionFont)).apply { backgroundColor = LIGHT_GREY; setPadding(4f) })
            }
            report.confirmedPayouts.forEach { line ->
                table.addCell(PdfPCell(Phrase(dateFmt.format(line.confirmedAt), valueFont)).apply { setPadding(4f) })
                table.addCell(PdfPCell(Phrase("${line.label} — ${line.payeeName}", valueFont)).apply { setPadding(4f) })
                table.addCell(PdfPCell(Phrase("%,.2f €".format(line.amount), valueFont)).apply { setPadding(4f) })
            }
            document.add(table)
        }

        val bannerTable = PdfPTable(1)
        bannerTable.widthPercentage = 100f
        bannerTable.addCell(
            PdfPCell(Phrase(report.registryBannerText, smallFont)).apply {
                backgroundColor = LIGHT_GREY
                border = Rectangle.BOX
                borderColor = MID_GREY
                setPadding(8f)
            }
        )
        document.add(bannerTable)

        document.close()
        return baos.toByteArray()
    }

    private fun kpiCell(label: String, value: String, captionFont: Font, valueFont: Font): PdfPCell =
        PdfPCell().apply {
            backgroundColor = LIGHT_GREY
            border = Rectangle.BOX
            borderColor = MID_GREY
            setPadding(8f)
            addElement(Paragraph(label, captionFont))
            addElement(Paragraph(value, valueFont))
        }
}

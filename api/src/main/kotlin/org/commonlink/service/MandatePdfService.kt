package org.commonlink.service

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfWriter
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.MandateEligibility
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates a PDF document for a signed fiscal mandate.
 *
 * Uses OpenPDF (LGPL/MPL) — not iText (AGPL). Pure function: no dependencies injected,
 * all input comes from the mandate and profile parameters.
 */
@Service
class MandatePdfService {

    private val parisZone = ZoneId.of("Europe/Paris")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(parisZone)

    /**
     * Generates the PDF bytes for the given mandate.
     *
     * @param mandate The active fiscal mandate to render.
     * @param profile The association that signed the mandate.
     * @return Raw PDF bytes ready to be sent as a response body.
     */
    fun generate(mandate: FiscalMandate, profile: AssociationProfile): ByteArray {
        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 50f, 50f, 60f, 60f)
        PdfWriter.getInstance(document, baos)
        document.open()

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)
        val subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12f)
        val sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f)
        val boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f)
        val normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val captionFont = FontFactory.getFont(FontFactory.HELVETICA, 9f)

        // ── Header ────────────────────────────────────────────────────────
        document.add(Paragraph("CommonLink", titleFont).apply { spacingAfter = 4f })
        document.add(Paragraph("Mandat d'émission des reçus fiscaux", subtitleFont).apply { spacingAfter = 24f })

        // ── Association identity ───────────────────────────────────────────
        document.add(Paragraph("Identité de l'association", sectionFont).apply { spacingAfter = 8f })
        document.add(Paragraph("Nom : ${profile.name}", normalFont))
        profile.rna?.let { document.add(Paragraph("N° RNA : $it", normalFont)) }
        document.add(Paragraph("SIREN / identifiant : ${profile.identifier}", normalFont).apply { spacingAfter = 20f })

        // ── Mandate details ───────────────────────────────────────────────
        document.add(Paragraph("Détails du mandat", sectionFont).apply { spacingAfter = 8f })
        document.add(Paragraph("Référence : ${mandate.reference}", normalFont))
        document.add(Paragraph("Signé le : ${dateFormatter.format(mandate.signedAt)}", normalFont))
        document.add(
            Paragraph("Éligibilité déclarée : ${eligibilityLabel(mandate.eligibility)}", normalFont)
                .apply { spacingAfter = 20f }
        )

        // ── Legal text ────────────────────────────────────────────────────
        document.add(Paragraph("Termes du mandat", sectionFont).apply { spacingAfter = 10f })

        document.add(legalParagraph(
            boldFont, normalFont,
            "Objet du mandat. ",
            "Par la signature électronique ci-dessous, l'association mandate CommonLink (SAS, RCS Bordeaux) " +
                "pour émettre, en son nom et pour son compte, les reçus fiscaux relatifs aux dons reçus via la plateforme.",
        ))
        document.add(legalParagraph(
            boldFont, normalFont,
            "Responsabilité. ",
            "L'association demeure seule juridiquement responsable de la délivrance des reçus et du respect " +
                "de son obligation déclarative annuelle prévue à l'article 222 bis du CGI.",
        ))
        document.add(legalParagraph(
            boldFont, normalFont,
            "Conservation. ",
            "Le présent mandat et les pièces justificatives sont conservés sur les serveurs CommonLink " +
                "pendant 6 ans minimum, conformément aux obligations fiscales.",
        ))
        document.add(legalParagraph(
            boldFont, normalFont,
            "Révocation. ",
            "Le mandat peut être révoqué à tout moment depuis l'espace paramètres. " +
                "La révocation prend effet immédiatement ; les reçus déjà émis demeurent valides.",
            spacingAfter = 24f,
        ))

        // ── Signature note ────────────────────────────────────────────────
        document.add(
            Paragraph("Signature électronique horodatée — ${dateFormatter.format(mandate.signedAt)}", captionFont)
        )

        document.close()
        return baos.toByteArray()
    }

    private fun legalParagraph(
        boldFont: com.lowagie.text.Font,
        normalFont: com.lowagie.text.Font,
        boldText: String,
        body: String,
        spacingAfter: Float = 8f,
    ): Paragraph {
        val p = Paragraph()
        p.add(Chunk(boldText, boldFont))
        p.add(Chunk(body, normalFont))
        p.spacingAfter = spacingAfter
        return p
    }

    private fun eligibilityLabel(eligibility: MandateEligibility): String = when (eligibility) {
        MandateEligibility.OIG_66 ->
            "Organisme d'intérêt général — réduction d'impôt de 66 % (Art. 200 CGI)"
        MandateEligibility.OIG_75_COLUCHE ->
            "Organisme d'aide aux personnes en difficulté (loi Coluche) — réduction d'impôt de 75 % (Art. 200-1 ter CGI)"
        MandateEligibility.PUBLIC_UTILITY_66 ->
            "Reconnu d'utilité publique — réduction d'impôt de 66 % (décret en Conseil d'État)"
    }
}

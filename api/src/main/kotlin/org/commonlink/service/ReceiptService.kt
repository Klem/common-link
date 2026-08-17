package org.commonlink.service

import com.lowagie.text.*
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import java.awt.Color
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Donation
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.MandateEligibility
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.util.FrenchAmountWords
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates a fiscal receipt PDF for a confirmed donation, established after the Cerfa 2041-RD model.
 *
 * The wording deliberately stops short of certifying conformity or entitlement: CommonLink produces
 * the document, it does not rule on the beneficiary's fiscal eligibility nor on the donor's own
 * situation. See [subtitle] and [donSection].
 *
 * The output is deterministic for a given donation — the same bytes are produced on retry,
 * which keeps the keccak256 hash stored on-chain stable.
 * Uses OpenPDF (LGPL/MPL — not iText AGPL).
 */
@Service
class ReceiptService(
    private val mandateRepository: FiscalMandateRepository,
    private val taxRateService: TaxRateService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val parisZone = ZoneId.of("Europe/Paris")
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(parisZone)
    private val genDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(parisZone)

    // ── Colours ────────────────────────────────────────────────────────────────
    private val DARK_NAVY  = Color(26, 46, 74)
    private val TEAL       = Color(6, 110, 145)
    private val WHITE      = Color(255, 255, 255)
    private val LIGHT_GREY = Color(245, 247, 250)
    private val MID_GREY   = Color(180, 180, 180)

    /**
     * Returns raw PDF bytes for [donation].
     *
     * @throws IllegalStateException if the association has no active fiscal mandate.
     */
    fun generate(donation: Donation, receiptNumber: String): ByteArray {
        logger.debug("Generating Cerfa receipt {} for donation {}", receiptNumber, donation.id)

        val association = donation.campaign.association
        val mandate = mandateRepository.findByAssociationIdAndRevokedAtIsNull(association.id!!)
            ?: error("No active fiscal mandate for association ${association.id}")

        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 50f, 50f, 50f, 50f)
        PdfWriter.getInstance(document, baos)
        document.open()

        val generatedOn = genDateFmt.format(java.time.Instant.now())

        headerBar(document, generatedOn)
        titleRow(document, receiptNumber)
        subtitle(document)
        separator(document)
        beneficiarySection(document, association, mandate)
        separator(document)
        donorSection(document, donation)
        separator(document)
        donSection(document, donation)
        separator(document)
        signatorySection(document, association)
        separator(document)
        footer(document)

        document.close()
        return baos.toByteArray()
    }

    // ── Sections ───────────────────────────────────────────────────────────────

    private fun headerBar(doc: Document, generatedOn: String) {
        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(1f, 2f))
        table.setSpacingAfter(12f)

        val boldWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f).apply { color = WHITE }
        val normalWhite = FontFactory.getFont(FontFactory.HELVETICA, 9f).apply { color = WHITE }

        doc.add(table.apply {
            addCell(navyCell(Phrase("CommonLink", boldWhite), Element.ALIGN_LEFT))
            addCell(navyCell(Phrase("Reçu fiscal généré le $generatedOn", normalWhite), Element.ALIGN_RIGHT))
        })
    }

    private fun titleRow(doc: Document, receiptNumber: String) {
        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(3f, 1f))
        table.setSpacingAfter(4f)

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)
        val numFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f).apply { color = TEAL }

        table.addCell(borderlessCell(Phrase("REÇU AU TITRE DES DONS", titleFont), Element.ALIGN_LEFT))
        table.addCell(borderlessCell(Phrase("N° $receiptNumber", numFont), Element.ALIGN_RIGHT).apply {
            verticalAlignment = Element.ALIGN_BOTTOM
        })
        doc.add(table)
    }

    private fun subtitle(doc: Document) {
        val small = FontFactory.getFont(FontFactory.HELVETICA, 8f)
        doc.add(Paragraph(
            "à certains organismes d'intérêt général — Articles 200, 238 bis et 978 du code général des impôts (CGI). " +
            "Établi selon le modèle Cerfa n° 11580*05.",
            small,
        ).apply { spacingAfter = 8f })
    }

    private fun beneficiarySection(doc: Document, a: AssociationProfile, m: FiscalMandate) {
        sectionHeader(doc, "BÉNÉFICIAIRE DU VERSEMENT")

        // Name + RNA/SIREN two-column
        val nameTable = PdfPTable(2)
        nameTable.widthPercentage = 100f
        nameTable.setSpacingAfter(6f)
        val captionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f).apply { color = TEAL }
        val valueFont   = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val valueBold   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f)

        nameTable.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("Dénomination", captionFont))
            addElement(Paragraph(a.name, valueBold))
        })
        nameTable.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("N° SIREN / RNA", captionFont))
            addElement(Paragraph(sirenRna(a), valueBold))
        })
        doc.add(nameTable)

        fieldRow(doc, "Adresse du siège", a.addressLine1.orEmpty().ifBlank { "${a.postalCode.orEmpty()} ${a.city.orEmpty()}".trim() })
        fieldRow(doc, "Objet", a.legalObject.orEmpty())
        fieldRow(doc, "Catégorie / situation", eligibilityText(m.eligibility))

        val certFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f)
        doc.add(Paragraph(
            "Le bénéficiaire certifie relever de l'une des catégories d'organismes mentionnées aux articles 200 et 238 bis du CGI, " +
            "sa gestion étant désintéressée et son activité non lucrative.",
            certFont,
        ).apply { spacingAfter = 6f })
    }

    private fun donorSection(doc: Document, d: Donation) {
        sectionHeader(doc, "DONATEUR")
        fieldRow(doc, "Nom et prénom", d.donorFullName.orEmpty())
        val address = buildString {
            d.donorAddressLine1?.let { append(it) }
            d.donorAddressLine2?.let { if (it.isNotBlank()) append(", $it") }
            val cityLine = "${d.donorPostalCode.orEmpty()} ${d.donorCity.orEmpty()}".trim()
            if (cityLine.isNotBlank()) append(", $cityLine")
        }
        fieldRow(doc, "Adresse", address)
    }

    private fun donSection(doc: Document, d: Donation) {
        sectionHeader(doc, "DON")

        // Amount box
        val amtTable = PdfPTable(2)
        amtTable.widthPercentage = 100f
        amtTable.setWidths(floatArrayOf(1f, 2f))
        amtTable.setSpacingAfter(8f)

        val bigFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20f)
        val wordsFont  = FontFactory.getFont(FontFactory.HELVETICA, 9f)
        val captionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f).apply { color = TEAL }

        val amtCell = PdfPCell().apply {
            backgroundColor = LIGHT_GREY
            border = Rectangle.BOX
            borderColor = MID_GREY
            setPadding(8f)
            addElement(Paragraph("Somme reçue", captionFont))
            addElement(Paragraph("%,.2f €".format(d.amount), bigFont))
        }
        val wordsCell = PdfPCell().apply {
            backgroundColor = LIGHT_GREY
            border = Rectangle.BOX
            borderColor = MID_GREY
            setPadding(8f)
            verticalAlignment = Element.ALIGN_MIDDLE
            addElement(Paragraph("(${FrenchAmountWords.format(d.amount)})", wordsFont))
        }
        amtTable.addCell(amtCell)
        amtTable.addCell(wordsCell)
        doc.add(amtTable)

        // Date / Forme — two columns
        val dateForm = PdfPTable(2)
        dateForm.widthPercentage = 100f
        dateForm.setSpacingAfter(4f)
        val normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10f)

        dateForm.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("Date du versement", captionFont))
            addElement(Paragraph(dateFmt.format(d.confirmedAt ?: d.createdAt), normalFont))
        })
        dateForm.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("Forme du don", captionFont))
            addElement(Paragraph("Déclaration de don manuel", normalFont))
        })
        doc.add(dateForm)

        // Nature / Mode — two columns
        val natMode = PdfPTable(2)
        natMode.widthPercentage = 100f
        natMode.setSpacingAfter(8f)
        natMode.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("Nature du don", captionFont))
            addElement(Paragraph("Numéraire", normalFont))
        })
        natMode.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("Mode de versement", captionFont))
            addElement(Paragraph(paymentMethodLabel(d.paymentMethod), normalFont))
        })
        doc.add(natMode)

        val smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8f)
        doc.add(Paragraph(
            "Le donateur atteste que ce versement, effectué sans contrepartie, relève d'une intention libérale. " +
            "Il est susceptible d'ouvrir droit à la réduction d'impôt prévue à l'article 200 du CGI " +
            "(${taxRateService.taxReductionRate(d.campaign.association)} % du montant), sous réserve du respect " +
            "des conditions légales et de la situation fiscale propre au donateur.",
            smallFont,
        ).apply { spacingAfter = 6f })
    }

    private fun signatorySection(doc: Document, a: AssociationProfile) {
        sectionHeader(doc, "SIGNATAIRE HABILITÉ")

        val captionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f).apply { color = TEAL }
        val normalFont  = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val boldFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f)

        val signerLine = buildString {
            a.signerName?.let { append(it) }
            a.signerRole?.let { if (it.isNotBlank()) append(" — $it") }
        }

        val topRow = PdfPTable(2)
        topRow.widthPercentage = 100f
        topRow.setSpacingAfter(8f)
        topRow.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("Nom et fonction", captionFont))
            addElement(Paragraph(signerLine, boldFont))
        })
        topRow.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("Fait à / le", captionFont))
            val city = a.city ?: ""
            val todayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(parisZone)
            addElement(Paragraph("${city.ifBlank { "" }.let { if (it.isNotBlank()) "$it, le " else "Le " }}${todayFmt.format(java.time.Instant.now())}", normalFont))
        })
        doc.add(topRow)

        // Signature box
        val sigTable = PdfPTable(1)
        sigTable.widthPercentage = 60f
        sigTable.horizontalAlignment = Element.ALIGN_LEFT
        sigTable.setSpacingAfter(10f)
        val sigCell = PdfPCell().apply {
            border = Rectangle.BOX
            borderColor = MID_GREY
            fixedHeight = 60f
            addElement(Paragraph("Signature :", captionFont))
        }
        sigTable.addCell(sigCell)
        doc.add(sigTable)
    }

    private fun footer(doc: Document) {
        val tiny = FontFactory.getFont(FontFactory.HELVETICA, 7f)
        doc.add(Paragraph(
            "Il est rappelé que la délivrance irrégulière de reçus fiscaux par l'organisme bénéficiaire est susceptible de donner lieu à l'application de l'amende " +
            "prévue à l'article 1740 A du code général des impôts. Le donateur doit conserver ce reçu ; l'organisme bénéficiaire déclare chaque année à l'administration " +
            "le montant global des dons et le nombre de reçus délivrés (article 222 bis du CGI).",
            tiny,
        ).apply { spacingAfter = 6f })
        doc.add(Paragraph("Reçu produit avec CommonLink. Ce document ne préjuge pas de l'éligibilité fiscale du bénéficiaire.", tiny))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun sectionHeader(doc: Document, title: String) {
        val table = PdfPTable(1)
        table.widthPercentage = 100f
        table.setSpacingBefore(6f)
        table.setSpacingAfter(6f)
        val font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f).apply { color = TEAL }
        val cell = PdfPCell(Phrase(title, font)).apply {
            border = Rectangle.BOTTOM
            borderColor = TEAL
            borderWidth = 0.8f
            paddingBottom = 3f
            backgroundColor = WHITE
        }
        table.addCell(cell)
        doc.add(table)
    }

    private fun separator(doc: Document) {
        val table = PdfPTable(1)
        table.widthPercentage = 100f
        table.setSpacingBefore(4f)
        table.setSpacingAfter(4f)
        val cell = PdfPCell(Phrase(" ")).apply {
            border = Rectangle.BOTTOM
            borderColor = MID_GREY
            borderWidth = 0.5f
            fixedHeight = 1f
        }
        table.addCell(cell)
        doc.add(table)
    }

    private fun fieldRow(doc: Document, label: String, value: String) {
        if (value.isBlank()) return
        val captionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f).apply { color = TEAL }
        val valueFont   = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        doc.add(Paragraph(label, captionFont))
        doc.add(Paragraph(value, valueFont).apply { spacingAfter = 4f })
    }

    private fun navyCell(phrase: Phrase, align: Int) = PdfPCell(phrase).apply {
        backgroundColor = DARK_NAVY
        border = Rectangle.NO_BORDER
        horizontalAlignment = align
        setPadding(8f)
    }

    private fun borderlessCell(phrase: Phrase, align: Int) = PdfPCell(phrase).apply {
        border = Rectangle.NO_BORDER
        horizontalAlignment = align
        setPadding(2f)
    }

    private fun sirenRna(a: AssociationProfile): String = when {
        a.siren != null -> a.siren!!
        a.identifier.startsWith("W") -> a.identifier
        else -> a.identifier
    }

    /**
     * French label for the payment method actually used, from the provider code stored on
     * [Donation.paymentMethod].
     *
     * The receipt must state the mode really used, not the list of modes the platform accepts.
     * A null code means no webhook payload was available when the donation was confirmed
     * (reconciler path): "Non précisé" is stated rather than guessed. An unknown code is printed
     * as-is so a new Mollie method never silently becomes a wrong statement.
     */
    private fun paymentMethodLabel(code: String?): String {
        if (code.isNullOrBlank()) return "Non précisé"
        return when (code.lowercase()) {
            "creditcard", "debitcard" -> "Carte bancaire"
            "banktransfer"            -> "Virement bancaire"
            "directdebit"             -> "Prélèvement SEPA"
            "bancontact", "ideal", "sofort", "eps", "giropay",
            "przelewy24", "trustly", "belfius", "kbc" -> "Virement bancaire en ligne"
            "paypal"                  -> "PayPal"
            "applepay"                -> "Apple Pay"
            else                      -> code
        }
    }

    private fun eligibilityText(e: MandateEligibility): String = when (e) {
        MandateEligibility.OIG_66 ->
            "Organisme d'intérêt général — art. 200 CGI"
        MandateEligibility.OIG_75_COLUCHE ->
            "Organisme d'aide aux personnes en difficulté (fourniture gratuite de repas, de soins ou favorisant le logement) — art. 200-1 ter"
        MandateEligibility.PUBLIC_UTILITY_66 ->
            "Reconnu d'utilité publique — décret en Conseil d'État"
    }

}

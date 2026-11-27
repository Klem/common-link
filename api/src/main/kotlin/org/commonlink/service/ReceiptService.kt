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
import org.commonlink.repository.AssociationSignatureRepository
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.util.FrenchAmountWords
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates a fiscal receipt PDF for a confirmed donation, established after the Cerfa 11580 model.
 *
 * The receipt is established by the association, which certifies its content ([certificationBlock], [footer]);
 * CommonLink edits and sends it on the association's behalf under the fiscal mandate. It states no tax-reduction
 * rate: the donor's reduction depends on their own situation.
 *
 * The output is deterministic for a given donation — the same bytes are produced on retry,
 * which keeps the keccak256 hash stored on-chain stable.
 * Uses OpenPDF (LGPL/MPL — not iText AGPL).
 */
@Service
class ReceiptService(
    private val mandateRepository: FiscalMandateRepository,
    private val signatureRepository: AssociationSignatureRepository,
    private val receiptPrerequisiteService: ReceiptPrerequisiteService,
) {

    private companion object {
        /**
         * CGI articles of the Cerfa 11580 certification, with whether the box is ticked. Only article 200 (income
         * tax reduction of individuals) applies: donations are collected from individuals, never from companies
         * (238 bis), and never for the IFI (978).
         */
        val CERTIFIED_ARTICLES = listOf(
            "200 du CGI" to true,
            "238 bis du CGI" to false,
            "978 du CGI" to false,
        )

        /** Side of a certification checkbox, in points. */
        const val CHECKBOX_SIZE = 8f

        /** Width of the label following a certification checkbox, in points. */
        const val CHECKBOX_LABEL_WIDTH = 72f
    }

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

    // ── Signature box ──────────────────────────────────────────────────────────
    // Same height as the empty box receipts always had: the signature never changes the page layout.
    // Caption line (~12pt) + image (≤ 40pt) + cell padding fit in it; a fixed-height cell would silently
    // drop content that does not fit.
    private val SIGNATURE_BOX_HEIGHT = 60f
    private val SIGNATURE_IMAGE_MAX_WIDTH = 260f
    private val SIGNATURE_IMAGE_MAX_HEIGHT = 40f

    /**
     * Returns raw PDF bytes for [donation].
     *
     * Never issues an incomplete receipt: it refuses when a mandatory element is missing rather than print an
     * empty signature box or a blank mention. Collection is gated upstream on the same rule
     * ([ReceiptPrerequisiteService.canIssueReceipts]), so a refusal here means the association's data changed
     * between payment and generation; the caller ([DonationReceiptService]) rolls back, logs at ERROR and its
     * reconciler retries until the data is fixed.
     *
     * @throws IllegalStateException if the association has no active fiscal mandate, misses a
     *   [org.commonlink.entity.ReceiptPrerequisite], or its saved signature image cannot be rendered.
     */
    fun generate(donation: Donation, receiptNumber: String): ByteArray {
        logger.debug("Generating Cerfa receipt {} for donation {}", receiptNumber, donation.id)

        val association = donation.campaign.association
        val mandate = mandateRepository.findByAssociationIdAndRevokedAtIsNull(association.id!!)
            ?: error("No active fiscal mandate for association ${association.id}")
        val missing = receiptPrerequisiteService.missing(association)
        check(missing.isEmpty()) { "Receipt prerequisites missing for association ${association.id}: $missing" }
        val signature = signatureImage(association)

        val baos = ByteArrayOutputStream()
        // Top/bottom margins kept at 36 pt (0.5 in) so a receipt with a long object and the donor's birth details
        // still fits on one page.
        val document = Document(PageSize.A4, 50f, 50f, 36f, 36f)
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
        signatorySection(document, association, signature)
        separator(document)
        footer(document, association)

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

        val cityLine = "${a.postalCode.orEmpty()} ${a.city.orEmpty()}".trim()
        fieldRow(doc, "Adresse du siège", listOf(a.addressLine1.orEmpty().trim(), cityLine).filter { it.isNotBlank() }.joinToString(", "))
        fieldRow(doc, "Objet", a.legalObject.orEmpty())
        fieldRow(doc, "Catégorie / situation", eligibilityText(m.eligibility))

        certificationBlock(doc)
    }

    /**
     * Cerfa 11580 certification: the beneficiary certifies on its honour which CGI article the donations open a
     * reduction under, followed by one checkbox per article of [CERTIFIED_ARTICLES] — only article 200 ticked.
     *
     * Helvetica (WinAnsi) has no ballot-box glyph, so each box is a small bordered table cell holding an "X" when
     * ticked: the extracted text reads "X 200 du CGI 238 bis du CGI 978 du CGI".
     */
    private fun certificationBlock(doc: Document) {
        val certFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f)
        doc.add(Paragraph(
            "Le bénéficiaire certifie sur l'honneur que les dons et versements qu'il reçoit ouvrent droit à la " +
                "réduction d'impôt prévue à l'article :",
            certFont,
        ).apply { spacingAfter = 3f })

        val markFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f)
        val labelFont = FontFactory.getFont(FontFactory.HELVETICA, 8f)
        val table = PdfPTable(CERTIFIED_ARTICLES.size * 2).apply {
            totalWidth = CERTIFIED_ARTICLES.size * (CHECKBOX_SIZE + CHECKBOX_LABEL_WIDTH)
            isLockedWidth = true
            horizontalAlignment = Element.ALIGN_LEFT
            setWidths(CERTIFIED_ARTICLES.flatMap { listOf(CHECKBOX_SIZE, CHECKBOX_LABEL_WIDTH) }.toFloatArray())
            setSpacingAfter(4f)
        }
        CERTIFIED_ARTICLES.forEach { (label, checked) ->
            table.addCell(PdfPCell(Phrase(CHECKBOX_SIZE, if (checked) "X" else "", markFont)).apply {
                border = Rectangle.BOX
                borderWidth = 0.6f
                fixedHeight = CHECKBOX_SIZE
                horizontalAlignment = Element.ALIGN_CENTER
                verticalAlignment = Element.ALIGN_MIDDLE
                isUseAscender = true
                setPadding(0f)
            })
            table.addCell(PdfPCell(Phrase(CHECKBOX_SIZE, label, labelFont)).apply {
                border = Rectangle.NO_BORDER
                verticalAlignment = Element.ALIGN_MIDDLE
                isUseAscender = true
                setPadding(0f)
                paddingLeft = 3f
            })
        }
        doc.add(table)
    }

    /**
     * "DONATEUR" block of the receipt: name and address side by side, then birth date and city side by side
     * ([fieldPair]).
     *
     * Birth details are printed **only when known**: the widget donation form does not keep them (see
     * [Donation.donorBirthDate]); only a donor with an account fills them in. An empty line would read as a statement
     * of missing information — nothing is printed rather than a label without a value.
     */
    private fun donorSection(doc: Document, d: Donation) {
        sectionHeader(doc, "DONATEUR")
        val address = buildString {
            d.donorAddressLine1?.let { append(it) }
            d.donorAddressLine2?.let { if (it.isNotBlank()) append(", $it") }
            val cityLine = "${d.donorPostalCode.orEmpty()} ${d.donorCity.orEmpty()}".trim()
            if (cityLine.isNotBlank()) append(", $cityLine")
        }
        fieldPair(doc, "Nom et prénom" to d.donorFullName.orEmpty(), "Adresse" to address)
        fieldPair(
            doc,
            "Date de naissance" to (d.donorBirthDate?.let { dateFmt.format(it) } ?: ""),
            "Ville de naissance" to d.donorBirthCity.orEmpty(),
        )
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
    }

    /**
     * "SIGNATAIRE HABILITÉ" block: signer name and role, place and date of issue, and the signature box holding
     * [signature] (already scaled by [signatureImage]).
     */
    private fun signatorySection(doc: Document, a: AssociationProfile, signature: Image) {
        sectionHeader(doc, "SIGNATAIRE HABILITÉ")

        val captionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f).apply { color = TEAL }
        val normalFont  = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val boldFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f)

        val signerLine = buildString {
            a.signerName?.let { append(it) }
            a.signerRole?.let { if (it.isNotBlank()) append(" — $it") }
        }

        // One row: name, role, place and date on the left, the signature box beside them — keeps the receipt on
        // a single page.
        val row = PdfPTable(2)
        row.widthPercentage = 100f
        row.setWidths(floatArrayOf(1f, 1.2f))
        row.setSpacingAfter(8f)
        row.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
            addElement(Paragraph("Nom et fonction", captionFont))
            addElement(Paragraph(signerLine, boldFont))
            addElement(Paragraph("Fait à / le", captionFont))
            val city = a.city ?: ""
            val todayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(parisZone)
            addElement(Paragraph("${city.ifBlank { "" }.let { if (it.isNotBlank()) "$it, le " else "Le " }}${todayFmt.format(java.time.Instant.now())}", normalFont))
        })
        // Signature box: always carries the saved signature (generate refuses otherwise).
        row.addCell(PdfPCell().apply {
            border = Rectangle.BOX
            borderColor = MID_GREY
            fixedHeight = SIGNATURE_BOX_HEIGHT
            // spacingAfter keeps the image clear of the caption's descenders.
            addElement(Paragraph("Signature :", captionFont).apply { spacingAfter = 2f })
            addElement(signature)
        })
        doc.add(row)
    }

    /**
     * The saved signature image of association [a] (UPLOAD or DRAWN), scaled down to fit the signature box with
     * its aspect ratio kept.
     *
     * Read before anything is written to the document, so a failure leaves no half-built receipt.
     *
     * @throws IllegalStateException if no signature is saved or its image cannot be read by OpenPDF — the receipt
     *   is refused rather than issued with an empty box.
     */
    private fun signatureImage(a: AssociationProfile): Image {
        val signature = signatureRepository.findById(a.id!!).orElse(null)
            ?: error("No receipt signature for association ${a.id}")
        return try {
            Image.getInstance(signature.image).apply {
                scaleToFit(SIGNATURE_IMAGE_MAX_WIDTH, SIGNATURE_IMAGE_MAX_HEIGHT)
                // In a composite cell OpenPDF otherwise stretches the image to the full cell width.
                widthPercentage = 0f
            }
        } catch (e: Exception) {
            throw IllegalStateException("Receipt signature of association ${a.id} cannot be rendered", e)
        }
    }

    /**
     * Legal reminder (art. 1740 A and 222 bis CGI), then the statement that [a] establishes the receipt and
     * certifies its content, CommonLink only editing and sending it under the mandate.
     */
    private fun footer(doc: Document, a: AssociationProfile) {
        val tiny = FontFactory.getFont(FontFactory.HELVETICA, 7f)
        doc.add(Paragraph(
            "Il est rappelé que la délivrance irrégulière de reçus fiscaux par l'organisme bénéficiaire est susceptible de donner lieu à l'application de l'amende " +
            "prévue à l'article 1740 A du code général des impôts. Le donateur doit conserver ce reçu ; l'organisme bénéficiaire déclare chaque année à l'administration " +
            "le montant global des dons et le nombre de reçus délivrés (article 222 bis du CGI).",
            tiny,
        ).apply { spacingAfter = 6f })
        doc.add(Paragraph(
            "Reçu établi par ${a.name}, qui en certifie le contenu sous sa seule responsabilité. " +
                "Édité et transmis par CommonLink au nom et pour le compte de l'association, en vertu d'un mandat.",
            tiny,
        ))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun sectionHeader(doc: Document, title: String) {
        val table = PdfPTable(1)
        table.widthPercentage = 100f
        table.setSpacingBefore(3f)
        table.setSpacingAfter(5f)
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
        table.setSpacingBefore(2f)
        table.setSpacingAfter(2f)
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

    /**
     * Two labelled values side by side (same look as [fieldRow]). A blank value prints nothing, label included, as in
     * [fieldRow]; the row is skipped altogether when both are blank.
     */
    private fun fieldPair(doc: Document, left: Pair<String, String>, right: Pair<String, String>) {
        if (left.second.isBlank() && right.second.isBlank()) return
        val captionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f).apply { color = TEAL }
        val valueFont   = FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setSpacingAfter(4f)
        listOf(left, right).forEach { (label, value) ->
            table.addCell(borderlessCell(Phrase(""), Element.ALIGN_LEFT).apply {
                if (value.isNotBlank()) {
                    addElement(Paragraph(label, captionFont))
                    addElement(Paragraph(value, valueFont))
                }
            })
        }
        doc.add(table)
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
     * as-is so a new Mollie or HelloAsso method never silently becomes a wrong statement.
     */
    private fun paymentMethodLabel(code: String?): String {
        if (code.isNullOrBlank()) return "Non précisé"
        return when (code.lowercase()) {
            // Mollie codes, then HelloAsso PaymentMeans (Card, Sepa, BankTransfer, Check, Cash). Same wording for
            // the same means; an unknown code is printed as is rather than guessed.
            "creditcard", "debitcard", "card" -> "Carte bancaire"
            "banktransfer"            -> "Virement bancaire"
            "directdebit", "sepa"     -> "Prélèvement SEPA"
            "check"                   -> "Chèque"
            "cash"                    -> "Espèces"
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
    }

}

package org.commonlink.service

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import io.mockk.every
import io.mockk.mockk
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationSignature
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.MandateEligibility
import org.commonlink.entity.SignatureMode
import org.commonlink.repository.AssociationSignatureRepository
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.util.FrenchAmountWords
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Asserts the *wording* of the fiscal receipt, on the text actually rendered into the PDF.
 *
 * The receipt is the only statement made to a donor about their tax position, so each sentence is load-bearing:
 * it is established and certified by the association (Cerfa 11580 certification, article 200 only), states no
 * reduction rate, must not claim the platform is free, and must state the payment mode really used. It is never
 * issued incomplete: no signature, legal object or head-office address means no receipt.
 *
 * Existing receipts keep their old wording by construction — [org.commonlink.entity.DonationReceipt]
 * stores immutable bytes whose keccak256 is on-chain — so these assertions only bind new receipts.
 */
class ReceiptServiceTest {

    private val mandateRepository = mockk<FiscalMandateRepository>()
    private val signatureRepository = mockk<AssociationSignatureRepository>()
    private val service = ReceiptService(
        mandateRepository,
        signatureRepository,
        ReceiptPrerequisiteService(signatureRepository, mandateRepository),
    )

    private val associationId: UUID = UUID.randomUUID()

    private var legalObject: String? = "Aide alimentaire"
    private var postalCode: String? = "75001"
    private var eligibility = MandateEligibility.OIG_66
    private var donorAddressLine2: String? = null

    init {
        // Default: a valid uploaded PNG signature — a receipt is never generated without one.
        withSignature(SignatureMode.UPLOAD, image("png"), "image/png")
        val mandate = mockk<FiscalMandate>(relaxed = true).also {
            every { it.eligibility } answers { eligibility }
        }
        every { mandateRepository.findByAssociationIdAndRevokedAtIsNull(associationId) } returns mandate
    }

    private fun association(): AssociationProfile = mockk<AssociationProfile>(relaxed = true).also {
        every { it.id } returns associationId
        every { it.name } returns "Les Restos Solidaires"
        every { it.identifier } returns "W751234567"
        every { it.siren } returns null
        every { it.addressLine1 } returns "12 rue de la Paix"
        every { it.postalCode } returns postalCode
        every { it.city } returns "Paris"
        every { it.legalObject } returns legalObject
        every { it.signerName } returns "Marie Martin"
        every { it.signerRole } returns "Présidente"
    }

    private fun donation(
        paymentMethod: String?,
        birthDate: LocalDate? = null,
        birthCity: String? = null,
        amount: BigDecimal = BigDecimal("42.00"),
    ): Donation {
        val assoc = association()
        val campaign = mockk<Campaign>(relaxed = true).also { every { it.association } returns assoc }
        return mockk<Donation>(relaxed = true).also {
            every { it.campaign } returns campaign
            every { it.amount } returns amount
            every { it.donorFullName } returns "Jean Dupont"
            every { it.donorBirthDate } returns birthDate
            every { it.donorBirthCity } returns birthCity
            every { it.donorAddressLine1 } returns "3 avenue des Fleurs"
            every { it.donorAddressLine2 } returns donorAddressLine2
            every { it.donorPostalCode } returns "69003"
            every { it.donorCity } returns "Lyon"
            every { it.confirmedAt } returns Instant.parse("2026-03-04T10:00:00Z")
            every { it.createdAt } returns Instant.parse("2026-03-04T10:00:00Z")
            every { it.paymentMethod } returns paymentMethod
        }
    }

    /** Raw bytes of a generated receipt. */
    private fun receiptBytes(
        paymentMethod: String? = "creditcard",
        birthDate: LocalDate? = null,
        birthCity: String? = null,
        amount: BigDecimal = BigDecimal("42.00"),
    ): ByteArray = service.generate(donation(paymentMethod, birthDate, birthCity, amount), "2026-0042")

    /** Text of the receipt, whitespace-normalised so line wrapping does not matter. */
    private fun receiptText(
        paymentMethod: String? = "creditcard",
        birthDate: LocalDate? = null,
        birthCity: String? = null,
        amount: BigDecimal = BigDecimal("42.00"),
    ): String {
        val bytes = receiptBytes(paymentMethod, birthDate, birthCity, amount)
        val reader = PdfReader(bytes)
        val text = (1..reader.numberOfPages)
            .joinToString(" ") { PdfTextExtractor(reader).getTextFromPage(it) }
        reader.close()
        return text.replace(Regex("\\s+"), " ")
    }

    /**
     * The association establishes the receipt and certifies it; CommonLink only edits and sends it under the mandate.
     * The former "produced with CommonLink / does not prejudge eligibility" sentence weakened that certification.
     */
    @Test
    fun `states that the association establishes and certifies the receipt under a mandate`() {
        val text = receiptText()

        assertTrue(text.contains(
            "Reçu établi par Les Restos Solidaires, qui en certifie le contenu sous sa seule responsabilité. " +
                "Édité et transmis par CommonLink au nom et pour le compte de l'association, en vertu d'un mandat."
        ), text)
        assertTrue(text.contains("article 1740 A du code général des impôts"), text)
        assertFalse(text.contains("Reçu produit avec CommonLink"), text)
        assertFalse(text.contains("préjuge"), text)
        assertFalse(text.contains("gratuit"), "Receipt must not claim CommonLink is free: $text")
    }

    /** The subtitle keeps the official Cerfa title, articles 200, 238 bis and 978 included (user decision). */
    @Test
    fun `keeps the official Cerfa title and states the model is followed, not that the document conforms`() {
        val text = receiptText()

        assertTrue(text.contains("Articles 200, 238 bis et 978 du code général des impôts (CGI)"), text)
        assertTrue(text.contains("Établi selon le modèle Cerfa"), text)
        assertFalse(text.contains("conforme au Cerfa"), text)
    }

    /**
     * Cerfa 11580 certification: three boxes, only article 200 ticked — donations come from individuals (no 238 bis,
     * companies) and never for the IFI (no 978). Each box is a bordered cell holding an "X" when ticked.
     */
    @Test
    fun `certifies on its honour the article 200 reduction with only the 200 box ticked`() {
        val text = receiptText()

        assertTrue(text.contains(
            "Le bénéficiaire certifie sur l'honneur que les dons et versements qu'il reçoit ouvrent droit à la " +
                "réduction d'impôt prévue à l'article :"
        ), text)
        // Exactly one "X", in the box before "200 du CGI"; the 238 bis and 978 boxes are empty.
        assertTrue(Regex("X\\s*200 du CGI\\s*238 bis du CGI\\s*978 du CGI").containsMatchIn(text), text)
        assertFalse(Regex("X\\s*238 bis").containsMatchIn(text), text)
        assertFalse(Regex("X\\s*978").containsMatchIn(text), text)
        assertFalse(text.contains("certifie relever"), text)
    }

    /** No percentage anywhere: the donor's reduction depends on their own situation, the receipt does not state it. */
    @Test
    fun `states no tax reduction rate and no donor attestation`() {
        val text = receiptText()

        assertFalse(text.contains("%"), text)
        assertFalse(text.contains("intention libérale"), text)
        assertFalse(text.contains("susceptible d'ouvrir droit"), text)
    }

    /** The head-office address is printed in full: street, postal code and city. */
    @Test
    fun `prints the full head-office address`() {
        assertTrue(receiptText().contains("Adresse du siège 12 rue de la Paix, 75001 Paris"))
    }

    /** The sample receipt fits on a single A4 page. */
    @Test
    fun `fits on a single page`() {
        assertEquals(1, pageCount(receiptBytes()))
    }

    /**
     * Heaviest common case still on one page: two-line object (~115 characters), two-line Coluche category, donor birth
     * details and a second address line, four-digit amount. [AssociationProfile.legalObject] is unbounded (TEXT): a much
     * longer object still runs onto a second page.
     */
    @Test
    fun `fits on a single page with a long object, the Coluche category and the donor birth details`() {
        legalObject = "Aide alimentaire et accompagnement des personnes en grande précarité dans le cadre de distributions hebdomadaires"
        eligibility = MandateEligibility.OIG_75_COLUCHE
        donorAddressLine2 = "Bâtiment B"

        val bytes = receiptBytes(birthDate = LocalDate.of(1985, 6, 15), birthCity = "Lyon", amount = BigDecimal("1234.56"))

        assertEquals(1, pageCount(bytes))
    }

    /**
     * The receipt prints the donation amount, never the amount paid at the provider: a voluntary HelloAsso
     * contribution (U9, observed absent from the paid amount on 10/11/2026) can never inflate it.
     */
    @Test
    fun `prints the donation amount of a HelloAsso donation paid with a contribution`() {
        val amount = BigDecimal("10.00")
        val text = receiptText(paymentMethod = "Card", amount = amount)

        assertTrue(text.contains("Somme reçue " + "%,.2f €".format(amount)), text)
        assertTrue(text.contains(FrenchAmountWords.format(amount)), text)
    }

    /** The mode really used, not the list of modes the platform accepts. */
    @Test
    fun `prints the payment method actually used`() {
        val text = receiptText(paymentMethod = "creditcard")

        assertTrue(text.contains("Mode de versement Carte bancaire"), text)
        assertFalse(text.contains("Virement, prélèvement ou carte bancaire"), text)
    }

    @Test
    fun `maps a bank transfer to its French label`() {
        assertTrue(receiptText(paymentMethod = "banktransfer").contains("Virement bancaire"))
    }

    /** HelloAsso PaymentMeans codes (Card, Sepa, BankTransfer, Check, Cash) share the Mollie wording. */
    @Test
    fun `maps HelloAsso payment means to their French labels`() {
        assertTrue(receiptText(paymentMethod = "Card").contains("Mode de versement Carte bancaire"))
        assertTrue(receiptText(paymentMethod = "Sepa").contains("Mode de versement Prélèvement SEPA"))
        assertTrue(receiptText(paymentMethod = "BankTransfer").contains("Mode de versement Virement bancaire"))
        assertTrue(receiptText(paymentMethod = "Check").contains("Mode de versement Chèque"))
        assertTrue(receiptText(paymentMethod = "Cash").contains("Mode de versement Espèces"))
    }

    /** An unknown provider code is printed verbatim rather than mislabelled. */
    @Test
    fun `prints an unknown provider code as-is`() {
        assertTrue(receiptText(paymentMethod = "newfangledpay").contains("newfangledpay"))
    }

    /**
     * Donations confirmed without a provider payload (reconciler path) have no known method. Stating
     * "Non précisé" is the only honest option — the previous text asserted three modes at once.
     */
    @Test
    fun `states the payment method is unknown when it was never captured`() {
        val text = receiptText(paymentMethod = null)

        assertTrue(text.contains("Mode de versement Non précisé"), text)
    }

    /**
     * The widget donation keeps no birth data: the receipt must be generated without it, and above all
     * must not print a label without a value, which would read as missing information.
     */
    @Test
    fun `says nothing about the birth when it is unknown`() {
        val text = receiptText()

        assertFalse(text.contains("Date de naissance"), text)
        assertFalse(text.contains("Ville de naissance"), text)
    }

    /** Filled in by a donor who has an account, the information enriches the receipt. */
    @Test
    fun `prints the birth details when they are known`() {
        val text = receiptText(birthDate = LocalDate.of(1985, 6, 15), birthCity = "Lyon")

        assertTrue(text.contains("Date de naissance 15/06/1985"), text)
        assertTrue(text.contains("Ville de naissance Lyon"), text)
    }

    // ── Signature box and refusals ────────────────────────────────────────────

    private fun withSignature(mode: SignatureMode, image: ByteArray, contentType: String) {
        val now = Instant.parse("2026-11-23T10:00:00Z")
        every { signatureRepository.existsById(associationId) } returns true
        every { signatureRepository.findById(associationId) } returns Optional.of(
            AssociationSignature(associationId, mode, image, contentType, now, now)
        )
    }

    private fun withoutSignature() {
        every { signatureRepository.existsById(associationId) } returns false
        every { signatureRepository.findById(associationId) } returns Optional.empty()
    }

    private fun image(format: String): ByteArray {
        val img = BufferedImage(400, 150, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, 400, 150)
            color = Color.BLACK
            drawLine(10, 120, 390, 30)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()
    }

    /** Uncompressed PDF dictionaries (OpenPDF writes no object streams), searchable as text. */
    private fun ByteArray.pdfSource(): String = String(this, Charsets.ISO_8859_1)

    private fun pageCount(bytes: ByteArray): Int = PdfReader(bytes).let { r -> r.numberOfPages.also { r.close() } }

    private val imageXObject = Regex("/Subtype\\s*/Image")

    @Test
    fun `embeds the uploaded signature image in the box`() {
        val bytes = receiptBytes()

        assertTrue(imageXObject.containsMatchIn(bytes.pdfSource()), "Expected an image XObject in the receipt")
        assertEquals(1, pageCount(bytes))
    }

    @Test
    fun `embeds a drawn JPEG signature`() {
        withSignature(SignatureMode.DRAWN, image("jpg"), "image/jpeg")

        val bytes = receiptBytes()

        assertTrue(imageXObject.containsMatchIn(bytes.pdfSource()), "Expected an image XObject in the receipt")
        assertEquals(1, pageCount(bytes))
    }

    /** No receipt with an empty signature box: generation refuses, the pipeline retries once a signature is saved. */
    @Test
    fun `refuses to generate the receipt without a saved signature`() {
        withoutSignature()

        val e = assertThrows<IllegalStateException> { receiptBytes() }
        assertTrue(e.message!!.contains("SIGNATURE"), e.message)
    }

    @Test
    fun `refuses to generate the receipt when the stored image cannot be rendered`() {
        withSignature(SignatureMode.UPLOAD, "not an image".toByteArray(), "image/png")

        assertThrows<IllegalStateException> { receiptBytes() }
    }

    @Test
    fun `refuses to generate the receipt without a legal object`() {
        legalObject = " "

        val e = assertThrows<IllegalStateException> { receiptBytes() }
        assertTrue(e.message!!.contains("LEGAL_OBJECT"), e.message)
    }

    @Test
    fun `refuses to generate the receipt with an incomplete head-office address`() {
        postalCode = null

        val e = assertThrows<IllegalStateException> { receiptBytes() }
        assertTrue(e.message!!.contains("HEAD_OFFICE_ADDRESS"), e.message)
    }

    @Test
    fun `refuses to generate the receipt without an active fiscal mandate`() {
        every { mandateRepository.findByAssociationIdAndRevokedAtIsNull(associationId) } returns null

        assertThrows<IllegalStateException> { receiptBytes() }
    }
}

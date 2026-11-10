package org.commonlink.service

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import io.mockk.every
import io.mockk.mockk
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.MandateEligibility
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.util.FrenchAmountWords
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Asserts the *wording* of the fiscal receipt, on the text actually rendered into the PDF.
 *
 * The receipt is the only statement CommonLink makes to a donor about their tax position, so each
 * sentence is load-bearing: it must not certify Cerfa conformity, must not promise a tax reduction,
 * must not claim the platform is free, and must state the payment mode really used.
 *
 * Existing receipts keep their old wording by construction — [org.commonlink.entity.DonationReceipt]
 * stores immutable bytes whose keccak256 is on-chain — so these assertions only bind new receipts.
 */
class ReceiptServiceTest {

    private val mandateRepository = mockk<FiscalMandateRepository>()
    private val taxRateService = mockk<TaxRateService>()
    private val service = ReceiptService(mandateRepository, taxRateService)

    private val associationId: UUID = UUID.randomUUID()

    private fun association(): AssociationProfile = mockk<AssociationProfile>(relaxed = true).also {
        every { it.id } returns associationId
        every { it.name } returns "Les Restos Solidaires"
        every { it.identifier } returns "W751234567"
        every { it.siren } returns null
        every { it.addressLine1 } returns "12 rue de la Paix"
        every { it.postalCode } returns "75001"
        every { it.city } returns "Paris"
        every { it.legalObject } returns "Aide alimentaire"
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
            every { it.donorAddressLine2 } returns null
            every { it.donorPostalCode } returns "69003"
            every { it.donorCity } returns "Lyon"
            every { it.confirmedAt } returns Instant.parse("2026-03-04T10:00:00Z")
            every { it.createdAt } returns Instant.parse("2026-03-04T10:00:00Z")
            every { it.paymentMethod } returns paymentMethod
        }
    }

    /** Text of the single-page receipt, whitespace-normalised so line wrapping does not matter. */
    private fun receiptText(
        paymentMethod: String? = "creditcard",
        birthDate: LocalDate? = null,
        birthCity: String? = null,
        amount: BigDecimal = BigDecimal("42.00"),
    ): String {
        val mandate = mockk<FiscalMandate>(relaxed = true).also {
            every { it.eligibility } returns MandateEligibility.OIG_66
        }
        every { mandateRepository.findByAssociationIdAndRevokedAtIsNull(associationId) } returns mandate
        every { taxRateService.taxReductionRate(any()) } returns 66

        val bytes = service.generate(donation(paymentMethod, birthDate, birthCity, amount), "2026-0042")
        val reader = PdfReader(bytes)
        val text = (1..reader.numberOfPages)
            .joinToString(" ") { PdfTextExtractor(reader).getTextFromPage(it) }
        reader.close()
        return text.replace(Regex("\\s+"), " ")
    }

    /** CommonLink invoices its service; claiming the tool is free was simply false. */
    @Test
    fun `does not claim the platform is a free tool`() {
        val text = receiptText()

        assertFalse(text.contains("gratuit"), "Receipt must not claim CommonLink is free: $text")
        assertTrue(text.contains("Reçu produit avec CommonLink."))
    }

    /**
     * CommonLink follows the Cerfa model; it is not in a position to certify that the document
     * conforms to it, which is an assessment for the administration.
     */
    @Test
    fun `states the Cerfa model is followed, not that the document conforms`() {
        val text = receiptText()

        assertTrue(text.contains("Établi selon le modèle Cerfa"), text)
        assertFalse(text.contains("conforme au Cerfa"), text)
    }

    /**
     * Entitlement depends on conditions and on the donor's own fiscal situation, neither of which
     * CommonLink knows. The rate may be stated; the entitlement may only be presented as possible.
     */
    @Test
    fun `presents the tax reduction as possible, not granted`() {
        val text = receiptText()

        assertTrue(text.contains("susceptible d'ouvrir droit"), text)
        assertTrue(text.contains("66 % du montant"), text)
        assertTrue(text.contains("sous réserve"), text)
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
     * Le don widget ne conserve aucune donnée de naissance : le reçu doit se générer sans elle, et
     * surtout ne pas afficher un libellé sans valeur, qui se lirait comme une information manquante.
     */
    @Test
    fun `says nothing about the birth when it is unknown`() {
        val text = receiptText()

        assertFalse(text.contains("Date de naissance"), text)
        assertFalse(text.contains("Ville de naissance"), text)
    }

    /** Renseignée par un donateur disposant d'un espace, l'information enrichit le reçu. */
    @Test
    fun `prints the birth details when they are known`() {
        val text = receiptText(birthDate = LocalDate.of(1985, 6, 15), birthCity = "Lyon")

        assertTrue(text.contains("Date de naissance 15/06/1985"), text)
        assertTrue(text.contains("Ville de naissance Lyon"), text)
    }
}

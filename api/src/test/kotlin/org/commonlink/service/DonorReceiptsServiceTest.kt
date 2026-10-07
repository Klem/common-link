package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.dto.AnnualReceiptsSummaryDto
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorReceiptsServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository = mockk<DonationRepository>()
    private val fiscalMandateRepository = mockk<FiscalMandateRepository>()
    private val taxRateService = mockk<TaxRateService>()
    private val annualReceiptsSummaryPdfService = mockk<AnnualReceiptsSummaryPdfService>()

    private val donorReadScope = DonorReadScope(donorProfileRepository, donationRepository)
    private val service = DonorReceiptsService(
        donorReadScope, donationRepository, fiscalMandateRepository, taxRateService, annualReceiptsSummaryPdfService,
    )

    private val userId = UUID.randomUUID()
    private val donorId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
    private val donorProfile = DonorProfile(user = donorUser).setId(donorId)

    private fun stubDonor() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donorProfile)
    }

    private fun row(amount: BigDecimal, confirmedAt: Instant, assocId: UUID = associationId): DonationRepository.ReceiptedDonationRow =
        mockk<DonationRepository.ReceiptedDonationRow>().also {
            every { it.getAssociationId() } returns assocId
            every { it.getAmount() } returns amount
            every { it.getConfirmedAt() } returns confirmedAt
        }

    private fun detailRow(
        amount: BigDecimal,
        confirmedAt: Instant,
        receiptNumber: String = "REC-0001",
        assocName: String = "Asso",
        assocId: UUID = associationId,
    ): DonationRepository.ReceiptedDonationDetailRow =
        mockk<DonationRepository.ReceiptedDonationDetailRow>().also {
            every { it.getAssociationId() } returns assocId
            every { it.getAssociationName() } returns assocName
            every { it.getAmount() } returns amount
            every { it.getConfirmedAt() } returns confirmedAt
            every { it.getReceiptNumber() } returns receiptNumber
        }

    @Test
    fun `groups two distinct years with correct amounts and counts`() {
        stubDonor()
        val row2024 = row(BigDecimal("100"), Instant.parse("2024-06-01T10:00:00Z"))
        val row2025a = row(BigDecimal("50"), Instant.parse("2025-03-01T10:00:00Z"))
        val row2025b = row(BigDecimal("75"), Instant.parse("2025-09-01T10:00:00Z"))
        every { donationRepository.findReceiptedRowsByDonorId(donorId) } returns listOf(row2024, row2025a, row2025b)
        every { fiscalMandateRepository.findAllByAssociationId(associationId) } returns emptyList()
        every { taxRateService.taxReductionRateAt(emptyList(), any()) } returns 66

        val summaries = service.getYearlySummaries(userId)

        assertThat(summaries).hasSize(2)
        val y2025 = summaries.first { it.year == 2025 }
        assertThat(y2025.donationCount).isEqualTo(2)
        assertThat(y2025.totalAmount).isEqualByComparingTo("125")
        val y2024 = summaries.first { it.year == 2024 }
        assertThat(y2024.donationCount).isEqualTo(1)
        assertThat(y2024.totalAmount).isEqualByComparingTo("100")
    }

    @Test
    fun `a year with no receipted donation is absent from the list`() {
        stubDonor()
        every { donationRepository.findReceiptedRowsByDonorId(donorId) } returns emptyList()

        assertThat(service.getYearlySummaries(userId)).isEmpty()
    }

    @Test
    fun `mixes 66 and 75 rates within the same year`() {
        stubDonor()
        val assoc66 = UUID.randomUUID()
        val assoc75 = UUID.randomUUID()
        val confirmedAt66 = Instant.parse("2025-06-01T10:00:00Z")
        val confirmedAt75 = Instant.parse("2025-07-01T10:00:00Z")
        val row66 = row(BigDecimal("100"), confirmedAt66, assoc66)
        val row75 = row(BigDecimal("100"), confirmedAt75, assoc75)
        every { donationRepository.findReceiptedRowsByDonorId(donorId) } returns listOf(row66, row75)
        every { fiscalMandateRepository.findAllByAssociationId(assoc66) } returns emptyList()
        every { fiscalMandateRepository.findAllByAssociationId(assoc75) } returns emptyList()
        every { taxRateService.taxReductionRateAt(emptyList(), confirmedAt66) } returns 66
        every { taxRateService.taxReductionRateAt(emptyList(), confirmedAt75) } returns 75

        val summaries = service.getYearlySummaries(userId)

        assertThat(summaries.single().estimatedDeduction).isEqualByComparingTo("141.00")
    }

    @Test
    fun `getAnnualSummaryPdf throws NotFoundException for a year with no receipt`() {
        stubDonor()
        every { donationRepository.findReceiptedDetailRowsByDonorId(donorId) } returns emptyList()

        assertThatThrownBy { service.getAnnualSummaryPdf(userId, 2025) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `getAnnualSummaryPdf builds a summary with receipt numbers and association names`() {
        stubDonor()
        val confirmedAt = Instant.parse("2025-06-01T10:00:00Z")
        val d = detailRow(BigDecimal("100"), confirmedAt, receiptNumber = "REC-2025-0007", assocName = "Les Restos")
        every { donationRepository.findReceiptedDetailRowsByDonorId(donorId) } returns listOf(d)
        every { fiscalMandateRepository.findAllByAssociationId(associationId) } returns emptyList()
        every { taxRateService.taxReductionRateAt(emptyList(), confirmedAt) } returns 66
        val captured = slot<AnnualReceiptsSummaryDto>()
        every { annualReceiptsSummaryPdfService.generate(capture(captured)) } returns "%PDF-fake".toByteArray()

        service.getAnnualSummaryPdf(userId, 2025)

        assertThat(captured.captured.year).isEqualTo(2025)
        val line = captured.captured.lines.single()
        assertThat(line.associationName).isEqualTo("Les Restos")
        assertThat(line.receiptNumber).isEqualTo("REC-2025-0007")
        assertThat(line.rate).isEqualTo(66)
        assertThat(line.deductionAmount).isEqualByComparingTo("66.00")
        assertThat(captured.captured.totalAmount).isEqualByComparingTo("100")
        assertThat(captured.captured.totalDeduction).isEqualByComparingTo("66.00")
    }

    /**
     * Regression: rounding each line's deduction before summing can disagree with
     * [getYearlySummaries] (which sums unrounded, then rounds once) by a cent on the same
     * donations -- exactly the figure a donor cross-checks between the tab and this PDF.
     */
    @Test
    fun `getAnnualSummaryPdf's total matches getYearlySummaries' rounding for the same donations`() {
        stubDonor()
        val amounts = listOf(BigDecimal("33.33"), BigDecimal("33.33"), BigDecimal("33.33"))
        val confirmedAts = listOf(
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-02-01T10:00:00Z"),
            Instant.parse("2025-03-01T10:00:00Z"),
        )
        val rows = amounts.zip(confirmedAts).map { (amount, at) -> row(amount, at) }
        val detailRows = amounts.zip(confirmedAts).map { (amount, at) -> detailRow(amount, at) }
        every { donationRepository.findReceiptedRowsByDonorId(donorId) } returns rows
        every { donationRepository.findReceiptedDetailRowsByDonorId(donorId) } returns detailRows
        every { fiscalMandateRepository.findAllByAssociationId(associationId) } returns emptyList()
        every { taxRateService.taxReductionRateAt(emptyList(), any()) } returns 66
        val captured = slot<AnnualReceiptsSummaryDto>()
        every { annualReceiptsSummaryPdfService.generate(capture(captured)) } returns byteArrayOf()

        val tabFigure = service.getYearlySummaries(userId).single().estimatedDeduction
        service.getAnnualSummaryPdf(userId, 2025)

        assertThat(captured.captured.totalDeduction).isEqualByComparingTo(tabFigure)
        assertThat(tabFigure).isEqualByComparingTo("65.99")
    }
}

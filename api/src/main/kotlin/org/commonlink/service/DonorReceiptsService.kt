package org.commonlink.service

import org.commonlink.dto.AnnualReceiptLineDto
import org.commonlink.dto.AnnualReceiptsSummaryDto
import org.commonlink.dto.DonorReceiptYearDto
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.security.DonorReadScope
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.util.UUID

private val PARIS_ZONE: ZoneId = ZoneId.of("Europe/Paris")

/**
 * Read-only service backing the donor's "Reçus fiscaux" tab: one summary row per year with at
 * least one receipted donation, plus a per-year PDF recap.
 *
 * Reuses [DonationRepository.findReceiptedRowsByDonorId] -- the same source as
 * [DonorDashboardService.getStats]'s global estimate -- grouped by the Paris-zone year of
 * [org.commonlink.entity.Donation.confirmedAt] instead of summed globally.
 */
@Service
class DonorReceiptsService(
    private val donorReadScope: DonorReadScope,
    private val donationRepository: DonationRepository,
    private val fiscalMandateRepository: FiscalMandateRepository,
    private val taxRateService: TaxRateService,
    private val annualReceiptsSummaryPdfService: AnnualReceiptsSummaryPdfService,
) {

    /** @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile. */
    @Transactional(readOnly = true)
    fun getYearlySummaries(userId: UUID): List<DonorReceiptYearDto> {
        val donorId = donorReadScope.resolve(userId).id!!
        val rows = donationRepository.findReceiptedRowsByDonorId(donorId)
        if (rows.isEmpty()) return emptyList()

        val mandatesByAssociation = rows.map { it.getAssociationId() }.distinct()
            .associateWith { fiscalMandateRepository.findAllByAssociationId(it) }

        return rows
            .groupBy { it.getConfirmedAt().atZone(PARIS_ZONE).year }
            .map { (year, yearRows) ->
                val deduction = yearRows
                    .map { row ->
                        val rate = taxRateService.taxReductionRateAt(mandatesByAssociation.getValue(row.getAssociationId()), row.getConfirmedAt())
                        row.getAmount().multiply(BigDecimal(rate)).divide(BigDecimal(100))
                    }
                    .fold(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP)
                DonorReceiptYearDto(
                    year = year,
                    donationCount = yearRows.size,
                    totalAmount = yearRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.getAmount() },
                    estimatedDeduction = deduction,
                )
            }
            .sortedByDescending { it.year }
    }

    /**
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     * @throws NotFoundException if the donor has no receipted donation in [year].
     */
    @Transactional(readOnly = true)
    fun getAnnualSummaryPdf(userId: UUID, year: Int): ByteArray {
        val donorId = donorReadScope.resolve(userId).id!!
        val yearRows = donationRepository.findReceiptedDetailRowsByDonorId(donorId)
            .filter { it.getConfirmedAt().atZone(PARIS_ZONE).year == year }
        if (yearRows.isEmpty()) throw NotFoundException("No receipted donation for donor $donorId in $year")

        val mandatesByAssociation = yearRows.map { it.getAssociationId() }.distinct()
            .associateWith { fiscalMandateRepository.findAllByAssociationId(it) }

        val lines = yearRows.map { row ->
            val rate = taxRateService.taxReductionRateAt(mandatesByAssociation.getValue(row.getAssociationId()), row.getConfirmedAt())
            val rawDeduction = row.getAmount().multiply(BigDecimal(rate)).divide(BigDecimal(100))
            AnnualReceiptLineDto(
                associationName = row.getAssociationName(),
                confirmedAt = row.getConfirmedAt(),
                amount = row.getAmount(),
                receiptNumber = row.getReceiptNumber(),
                rate = rate,
                // Rounded for per-line display only -- summed unrounded below, exactly like
                // getYearlySummaries, so the tab figure and this PDF's total never disagree by a
                // rounding cent on the same donations.
                deductionAmount = rawDeduction.setScale(2, RoundingMode.HALF_UP),
            )
        }
        val totalDeduction = yearRows
            .map { row ->
                val rate = taxRateService.taxReductionRateAt(mandatesByAssociation.getValue(row.getAssociationId()), row.getConfirmedAt())
                row.getAmount().multiply(BigDecimal(rate)).divide(BigDecimal(100))
            }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP)

        val summary = AnnualReceiptsSummaryDto(
            year = year,
            lines = lines,
            totalAmount = lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.amount },
            totalDeduction = totalDeduction,
        )
        return annualReceiptsSummaryPdfService.generate(summary)
    }
}

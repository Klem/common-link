package org.commonlink.service

import org.commonlink.dto.AssociationOptionDto
import org.commonlink.dto.DonorDonationDto
import org.commonlink.dto.DonorDonationFiltersDto
import org.commonlink.dto.DonorStatsDto
import org.commonlink.dto.ReceiptDownloadDto
import org.commonlink.entity.Donation
import org.commonlink.entity.DonationReceipt
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationReceiptRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.security.DonorReadScope
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Upper bound of the page size accepted on the donation history. */
private const val MAX_PAGE_SIZE = 100

/** Oldest year the year filter accepts — CommonLink has no donation predating its own existence. */
private const val MIN_DONATION_YEAR = 2020

/**
 * Read-only service backing the donor's "My donations" screens: history, filters, headline
 * figures, and download of a donation's fiscal receipt.
 *
 * Every entry point resolves the caller through [DonorReadScope]; no method takes a campaign or an
 * association id, so a donor can only ever read their own data.
 */
@Service
class DonorDashboardService(
    private val donorReadScope: DonorReadScope,
    private val donationRepository: DonationRepository,
    private val donationReceiptRepository: DonationReceiptRepository,
    private val fiscalMandateRepository: FiscalMandateRepository,
    private val taxRateService: TaxRateService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the donor's confirmed donations, newest first, with both filters optional.
     *
     * @param userId UUID of the authenticated user.
     * @param associationId restrict to one association; null keeps them all.
     * @param year restrict to one calendar year; null keeps them all.
     * @param page zero-based page index; a negative value is rejected.
     * @param size page size, clamped to 1..[MAX_PAGE_SIZE].
     * @return a page of [DonorDonationDto], receipt availability resolved for each row.
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     * @throws IllegalArgumentException if [page], [size] or [year] is out of range.
     */
    @Transactional(readOnly = true)
    fun listDonations(
        userId: UUID,
        associationId: UUID?,
        year: Int?,
        page: Int,
        size: Int,
    ): Page<DonorDonationDto> {
        val donor = donorReadScope.resolve(userId)
        validatePaging(page, size)
        validateYear(year)

        // The ordering is fixed by the query itself — the Pageable carries page and size only.
        val donations = donationRepository.findByDonorIdFiltered(
            donor.id!!, associationId, year, PageRequest.of(page, size.coerceIn(1, MAX_PAGE_SIZE)),
        )

        // One receipt lookup for the whole page, not one per row.
        val receiptNumbers = receiptNumbersFor(donations.content)
        return donations.map { it.toDonorDonationDto(receiptNumbers[it.id]) }
    }

    /**
     * Returns the values offered by the two history filters: associations funded, and years with
     * at least one confirmed donation.
     *
     * @param userId UUID of the authenticated user.
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     */
    @Transactional(readOnly = true)
    fun getFilters(userId: UUID): DonorDonationFiltersDto {
        val donorId = donorReadScope.resolve(userId).id!!
        return DonorDonationFiltersDto(
            associations = donationRepository.findDistinctAssociationsByDonorId(donorId)
                .map { AssociationOptionDto(id = it.getId(), name = it.getName()) },
            years = donationRepository.findDistinctYearsByDonorId(donorId),
        )
    }

    /**
     * Returns the donor's headline figures, including the estimated tax reduction.
     *
     * @param userId UUID of the authenticated user.
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     */
    @Transactional(readOnly = true)
    fun getStats(userId: UUID): DonorStatsDto {
        val donorId = donorReadScope.resolve(userId).id!!
        return DonorStatsDto(
            totalDonated = donationRepository.sumConfirmedAmountByDonorId(donorId) ?: BigDecimal.ZERO,
            donationCount = donationRepository.countConfirmedByDonorId(donorId),
            associationCount = donationRepository.countDistinctAssociationsByDonorId(donorId),
            estimatedTaxReduction = estimateTaxReduction(donorId),
        )
    }

    /**
     * Returns the stored Cerfa receipt of one of the donor's donations, ready to be downloaded.
     *
     * @param userId UUID of the authenticated user.
     * @param donationId donation whose receipt is requested.
     * @return the file name and the exact bytes whose hash was written on-chain.
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     * @throws NotFoundException if the donation does not exist, or carries no receipt yet.
     * @throws org.springframework.security.access.AccessDeniedException if the donation belongs to
     *   another donor — checked **before** receipt existence, so a probe learns nothing.
     */
    @Transactional(readOnly = true)
    fun getReceipt(userId: UUID, donationId: UUID): ReceiptDownloadDto {
        val donorId = donorReadScope.resolve(userId).id!!
        donorReadScope.assertOwnsDonation(donorId, donationId)
        val receipt = donationReceiptRepository.findByDonationId(donationId)
            ?: throw NotFoundException("No receipt generated for donation $donationId")
        return ReceiptDownloadDto(fileName = "recu-${receipt.receiptNumber}.pdf", pdfBytes = receipt.pdfBytes)
    }

    /**
     * Sum, over the donor's receipted donations only, of `amount × rate in force at the donation
     * date`.
     *
     * A donation without a [DonationReceipt] contributes nothing: the Cerfa receipt *is* the
     * instrument of the reduction, and none is issued without an active fiscal mandate. Counting
     * one would show a reduction next to a row the donor can see carries no receipt.
     *
     * The rate is per line, never global — a donor funding both a 66 % and a 75 % association mixes
     * them. Each association's mandate history is loaded once, so the number of queries is the
     * number of distinct associations, not the number of donations.
     */
    private fun estimateTaxReduction(donorId: UUID): BigDecimal {
        val rows = donationRepository.findReceiptedRowsByDonorId(donorId)
        if (rows.isEmpty()) return BigDecimal.ZERO

        val mandatesByAssociation = rows.map { it.getAssociationId() }.distinct()
            .associateWith { fiscalMandateRepository.findAllByAssociationId(it) }

        return rows
            .map { row ->
                val rate = taxRateService.taxReductionRateAt(
                    mandatesByAssociation.getValue(row.getAssociationId()), row.getConfirmedAt(),
                )
                row.getAmount().multiply(BigDecimal(rate)).divide(BigDecimal(100))
            }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP)
    }

    /** Receipt numbers of the given donations, keyed by donation id. Absent key = no receipt yet. */
    private fun receiptNumbersFor(donations: List<Donation>): Map<UUID, String> {
        if (donations.isEmpty()) return emptyMap()
        return donationReceiptRepository.findRefsByDonationIds(donations.map { it.id!! })
            .associate { it.getDonationId() to it.getReceiptNumber() }
    }

    private fun Donation.toDonorDonationDto(receiptNumber: String?) = DonorDonationDto(
        id = id!!,
        // Only confirmed donations reach this mapper — the queries filter on confirmedAt.
        donatedAt = confirmedAt!!,
        amount = amount,
        campaignId = campaign.id!!,
        campaignName = campaign.name,
        campaignEmoji = campaign.emoji,
        associationId = campaign.association.id!!,
        associationName = campaign.association.name,
        receiptAvailable = receiptNumber != null,
        receiptNumber = receiptNumber,
    )

    /**
     * Rejects paging values the UI cannot legitimately produce.
     *
     * Mirrors the front-end bounds server-side (project rule 8): every click is replayable, and an
     * unbounded `size` turns the history into a full-table export.
     */
    private fun validatePaging(page: Int, size: Int) {
        require(page >= 0) { "page must be zero or greater" }
        require(size in 1..MAX_PAGE_SIZE) { "size must be between 1 and $MAX_PAGE_SIZE" }
    }

    /** Rejects a year outside the plausible range, which could only come from a forged request. */
    private fun validateYear(year: Int?) {
        if (year == null) return
        val currentYear = Instant.now().atZone(ZoneOffset.UTC).year
        require(year in MIN_DONATION_YEAR..currentYear) {
            "year must be between $MIN_DONATION_YEAR and $currentYear"
        }
        log.debug("Donation history filtered on year {}", year)
    }
}

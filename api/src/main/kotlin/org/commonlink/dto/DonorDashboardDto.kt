package org.commonlink.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * One row of the donor's donation history.
 *
 * @property receiptNumber internal Cerfa number. Exposed so the UI can label the download, never as
 *   a path segment: the receipt is fetched by donation id, through the donor read scope.
 */
data class DonorDonationDto(
    val id: UUID,
    val donatedAt: Instant,
    val amount: BigDecimal,
    val campaignId: UUID,
    val campaignName: String,
    val campaignEmoji: String,
    val associationId: UUID,
    val associationName: String,
    val receiptAvailable: Boolean,
    val receiptNumber: String?,
)

/**
 * Headline figures of the "My donations" page.
 *
 * @property estimatedTaxReduction an **estimate**: the sum, over receipted donations only, of the
 *   amount times the rate of the fiscal mandate in force at the donation date. The 20 %-of-income
 *   ceiling is not applied here, so this is never a guaranteed amount.
 */
data class DonorStatsDto(
    val totalDonated: BigDecimal,
    val donationCount: Long,
    val associationCount: Long,
    val estimatedTaxReduction: BigDecimal,
)

/** Values available in the history filter selectors. */
data class DonorDonationFiltersDto(
    val associations: List<AssociationOptionDto>,
    val years: List<Int>,
)

/**
 * A receipt ready to be streamed to the donor.
 *
 * [pdfBytes] are the stored bytes, never a regeneration: their keccak256 hash is what was written
 * on-chain, so re-rendering the PDF would break the proof.
 */
data class ReceiptDownloadDto(
    val fileName: String,
    val pdfBytes: ByteArray,
) {
    // ByteArray uses identity equality; a data class holding one needs these to behave sanely.
    override fun equals(other: Any?): Boolean =
        this === other || (other is ReceiptDownloadDto && fileName == other.fileName && pdfBytes.contentEquals(other.pdfBytes))

    override fun hashCode(): Int = 31 * fileName.hashCode() + pdfBytes.contentHashCode()
}

/** An association as offered in the history filter. */
data class AssociationOptionDto(
    val id: UUID,
    val name: String,
)

/**
 * A supported association, as shown on the "My associations" page.
 *
 * @property category category of the **campaign the donor funded most recently** for this
 *   association. The attribute belongs to the campaign, not to the association, so there is no
 *   association-level category to report; null when that campaign carries none.
 * @property publishedPayoutCount confirmed payouts of the association, all campaigns combined.
 */
data class DonorAssociationDto(
    val associationId: UUID,
    val name: String,
    val category: String?,
    val totalDonated: BigDecimal,
    val publishedPayoutCount: Long,
    val campaignCount: Int,
    val lastDonationAt: Instant?,
)

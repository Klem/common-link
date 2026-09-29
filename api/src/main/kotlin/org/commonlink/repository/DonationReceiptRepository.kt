package org.commonlink.repository

import org.commonlink.entity.DonationReceipt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface DonationReceiptRepository : JpaRepository<DonationReceipt, UUID> {

    /** Returns the receipt for a donation, or null if not yet generated. */
    fun findByDonationId(donationId: UUID): DonationReceipt?

    /** Donation id and receipt number, without the PDF bytes. */
    interface ReceiptRefRow {
        fun getDonationId(): UUID
        fun getReceiptNumber(): String
    }

    /**
     * Receipt references for a batch of donations, one query for the whole page.
     *
     * A projection rather than the entity on purpose: the donation history only needs to know
     * whether a receipt exists and under which number. Loading [DonationReceipt] would drag one
     * PDF blob per row into memory to answer a boolean.
     *
     * @param donationIds must not be empty — callers guard, an empty `IN` list has no meaning.
     */
    @Query("""
        SELECT r.donation.id   AS donationId,
               r.receiptNumber AS receiptNumber
        FROM DonationReceipt r
        WHERE r.donation.id IN :donationIds
    """)
    fun findRefsByDonationIds(@Param("donationIds") donationIds: Collection<UUID>): List<ReceiptRefRow>
}

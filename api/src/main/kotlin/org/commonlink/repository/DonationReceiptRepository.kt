package org.commonlink.repository

import org.commonlink.entity.DonationReceipt
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DonationReceiptRepository : JpaRepository<DonationReceipt, UUID> {

    /** Returns the receipt for a donation, or null if not yet generated. */
    fun findByDonationId(donationId: UUID): DonationReceipt?
}

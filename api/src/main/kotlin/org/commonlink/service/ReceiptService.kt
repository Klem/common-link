package org.commonlink.service

import org.commonlink.entity.Donation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Generates Cerfa 2041-RD receipt PDF bytes for a confirmed donation.
 *
 * Current implementation is a deterministic stub that encodes the donation ID and amount.
 * Replace with a real PDF library (e.g. iText, OpenPDF) once the receipt template is finalised.
 */
@Service
class ReceiptService {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns raw PDF bytes for [donation].
     *
     * The output is deterministic for a given donation so that the keccak256 hash stored
     * on-chain is stable across retries (reconciler re-generates the same bytes → same hash).
     */
    fun generate(donation: Donation): ByteArray {
        logger.debug("Generating receipt stub for donation {}", donation.id)
        val content = buildString {
            append("RECEIPT:")
            append(donation.id)
            append("|CAMPAIGN:")
            append(donation.campaign.id)
            append("|AMOUNT:")
            append(donation.amount.toPlainString())
            append("|DONOR:")
            append(donation.donorFullName ?: "UNKNOWN")
        }
        return content.toByteArray(Charsets.UTF_8)
    }
}

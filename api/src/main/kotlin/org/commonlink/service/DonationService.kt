package org.commonlink.service

import org.commonlink.entity.OnchainJobAction
import org.commonlink.exception.NotFoundException
import org.commonlink.onchain.DonorAddressGenerator
import org.commonlink.onchain.OnchainCodec
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.time.Instant
import java.util.UUID

/**
 * Handles donation lifecycle, including on-chain recording when a payment is confirmed.
 */
@Service
class DonationService(
    private val donationRepository: DonationRepository,
    private val donorProfileRepository: DonorProfileRepository,
    private val donorAddressGenerator: DonorAddressGenerator,
    private val outbox: OnchainOutboxService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Marks a donation as confirmed and enqueues an on-chain RECORD_DONATION job.
     *
     * Derives a deterministic wallet address for the donor if one has not been assigned yet.
     * The [receiptPdfBytes] are hashed (keccak256) and recorded on-chain as the receipt proof.
     *
     * @param donationId UUID of the [org.commonlink.entity.Donation] to confirm.
     * @param receiptPdfBytes Raw bytes of the Cerfa receipt PDF; hashed before sending on-chain.
     * @throws NotFoundException if the donation is not found.
     */
    @Transactional
    fun confirmDonation(donationId: UUID, receiptPdfBytes: ByteArray) {
        val donation = donationRepository.findById(donationId)
            .orElseThrow { NotFoundException("Donation not found: $donationId") }

        val donor = donation.donor
        if (donor.walletAddress == null) {
            donor.walletAddress = donorAddressGenerator.generate(donor.id!!)
            donorProfileRepository.save(donor)
            logger.info("Derived wallet address for donor {}", donor.id)
        }

        donation.confirmedAt = Instant.now()
        donationRepository.save(donation)

        val receiptHashHex = Numeric.toHexString(Hash.sha3(receiptPdfBytes))
        outbox.enqueue(
            action = OnchainJobAction.RECORD_DONATION,
            payload = RecordDonationPayload(
                donationId     = donation.id!!,
                donor          = donor.walletAddress!!,
                campaignId     = donation.campaign.id!!,
                amountCents    = OnchainCodec.eurToCents(donation.amount),
                receiptHashHex = receiptHashHex,
                txRef          = donation.providerRef,
            ),
            correlationKey = "DONATION:${donation.id}",
        )
        logger.info("Enqueued RECORD_DONATION job for donation {}", donation.id)
    }
}

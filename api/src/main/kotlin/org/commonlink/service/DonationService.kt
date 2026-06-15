package org.commonlink.service

import org.commonlink.entity.Donation
import org.commonlink.entity.OnchainJobAction
import org.commonlink.exception.NotFoundException
import org.commonlink.onchain.DonorAddressGenerator
import org.commonlink.onchain.OnchainCodec
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Handles donation lifecycle, including on-chain recording when a payment is confirmed.
 */
@Service
class DonationService(
    private val donationRepository: DonationRepository,
    private val donorProfileRepository: DonorProfileRepository,
    private val campaignRepository: CampaignRepository,
    private val donorAddressGenerator: DonorAddressGenerator,
    private val outbox: OnchainOutboxService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Idempotent entry point for payment webhook handlers.
     *
     * Finds or creates the [Donation] row for [providerRef], then confirms it.
     * Safe to call multiple times for the same payment:
     * - If the donation already exists and is confirmed → no-op (no duplicate RECORD_DONATION).
     * - If the donation exists but is not yet confirmed → confirms it.
     * - If the donation does not exist → creates it, then confirms.
     *
     * @param providerRef Payment provider reference ("stripe:pi_..." / "monerium:<uuid>").
     * @param donorProfileId UUID of the [org.commonlink.entity.DonorProfile].
     * @param campaignId UUID of the [org.commonlink.entity.Campaign].
     * @param amount Donation amount in EUR.
     * @param receiptPdfBytes Raw bytes of the Cerfa receipt PDF; keccak256-hashed before going on-chain.
     */
    @Transactional
    fun recordPayment(
        providerRef: String,
        donorProfileId: UUID,
        campaignId: UUID,
        amount: BigDecimal,
        receiptPdfBytes: ByteArray,
    ) {
        val existing = donationRepository.findByProviderRef(providerRef)
        if (existing != null) {
            if (existing.confirmedAt != null) {
                logger.info("Skipping already-confirmed donation providerRef={}", providerRef)
                return
            }
            confirmDonation(existing.id!!, receiptPdfBytes)
            return
        }

        val donor = donorProfileRepository.findById(donorProfileId)
            .orElseThrow { NotFoundException("Donor profile not found: $donorProfileId") }
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }

        val donation = donationRepository.save(
            Donation(donor = donor, campaign = campaign, amount = amount, providerRef = providerRef)
        )
        confirmDonation(donation.id!!, receiptPdfBytes)
    }

    /**
     * Marks a donation as confirmed and enqueues an on-chain RECORD_DONATION job.
     *
     * Derives a deterministic wallet address for the donor if one has not been assigned yet.
     * The [receiptPdfBytes] are hashed (keccak256) and recorded on-chain as the receipt proof.
     * Idempotent: if the donation is already confirmed the call is a no-op.
     *
     * @param donationId UUID of the [org.commonlink.entity.Donation] to confirm.
     * @param receiptPdfBytes Raw bytes of the Cerfa receipt PDF; hashed before sending on-chain.
     * @throws NotFoundException if the donation is not found.
     */
    @Transactional
    fun confirmDonation(donationId: UUID, receiptPdfBytes: ByteArray) {
        val donation = donationRepository.findById(donationId)
            .orElseThrow { NotFoundException("Donation not found: $donationId") }

        if (donation.confirmedAt != null) {
            logger.info("Donation {} already confirmed, skipping", donationId)
            return
        }

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

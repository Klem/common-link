package org.commonlink.service

import org.commonlink.entity.Donation
import org.commonlink.event.DonationConfirmedEvent
import org.commonlink.exception.NotFoundException
import org.commonlink.onchain.DonorAddressGenerator
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Snapshot of donor identity fields captured at donation time for the Cerfa 2041-RD receipt.
 *
 * Stored on the [Donation] row — immutable fiscal truth independent of later profile changes.
 */
data class DonorIdentitySnapshot(
    val fullName: String,
    val addressLine1: String,
    val addressLine2: String?,
    val postalCode: String,
    val city: String,
    val country: String,
    val birthDate: LocalDate,
    val birthCity: String,
)

/**
 * Handles donation lifecycle, including on-chain recording when a payment is confirmed.
 */
@Service
class DonationService(
    private val donationRepository: DonationRepository,
    private val donorProfileRepository: DonorProfileRepository,
    private val campaignRepository: CampaignRepository,
    private val donorAddressGenerator: DonorAddressGenerator,
    private val publisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Idempotent entry point for payment webhook handlers.
     *
     * Finds or creates the [Donation] row for [providerRef], then confirms it.
     * Safe to call multiple times for the same payment:
     * - If the donation already exists and is confirmed → no-op.
     * - If the donation exists but is not yet confirmed → confirms it.
     * - If the donation does not exist → creates it, then confirms.
     *
     * After commit, a [DonationConfirmedEvent] is published for async receipt generation.
     *
     * @param providerRef Payment provider reference (e.g. "mollie:tr_xxx").
     * @param donorProfileId UUID of the [org.commonlink.entity.DonorProfile].
     * @param campaignId UUID of the [org.commonlink.entity.Campaign].
     * @param amount Donation amount in EUR.
     */
    @Transactional
    fun recordPayment(
        providerRef: String,
        donorProfileId: UUID,
        campaignId: UUID,
        amount: BigDecimal,
    ) {
        val existing = donationRepository.findByProviderRef(providerRef)
        if (existing != null) {
            if (existing.confirmedAt != null) {
                logger.info("Skipping already-confirmed donation providerRef={}", providerRef)
                return
            }
            confirmDonation(existing.id!!)
            return
        }

        val donor = donorProfileRepository.findById(donorProfileId)
            .orElseThrow { NotFoundException("Donor profile not found: $donorProfileId") }
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }

        val donation = donationRepository.save(
            Donation(donor = donor, campaign = campaign, amount = amount, providerRef = providerRef)
        )
        confirmDonation(donation.id!!)
    }

    /**
     * Marks a donation as confirmed and derives the donor wallet address.
     *
     * Publishes [DonationConfirmedEvent] after the transaction commits so that receipt
     * generation and on-chain enqueue happen asynchronously without blocking the caller.
     * Idempotent: if the donation is already confirmed the call is a no-op.
     *
     * @param donationId UUID of the [Donation] to confirm.
     * @throws NotFoundException if the donation is not found.
     */
    @Transactional
    fun confirmDonation(donationId: UUID) {
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

        publisher.publishEvent(DonationConfirmedEvent(donation.id!!))
        logger.info("Confirmed donation {} — receipt generation enqueued async", donation.id)
    }

    /**
     * Creates a pending [Donation] row for a widget payment that has been initiated with Mollie
     * but not yet confirmed. The donation remains pending until the Mollie webhook fires (B6).
     *
     * Idempotent at the [providerRef] level: a second call with the same providerRef is a no-op
     * (the existing pending row is returned as-is).
     *
     * @param providerRef Mollie payment reference in format "mollie:tr_xxx".
     * @param donorProfileId UUID of the guest [org.commonlink.entity.DonorProfile].
     * @param campaignId UUID of the destination [org.commonlink.entity.Campaign].
     * @param amount Donation amount in EUR.
     * @param sourceSite Sanitised origin site from the widget snippet (nullable, untrusted).
     * @param identity Fiscal identity snapshot required for the Cerfa 2041-RD receipt.
     */
    @Transactional
    fun initiatePendingDonation(
        providerRef: String,
        donorProfileId: UUID,
        campaignId: UUID,
        amount: BigDecimal,
        sourceSite: String?,
        identity: DonorIdentitySnapshot,
    ): Donation {
        val existing = donationRepository.findByProviderRef(providerRef)
        if (existing != null) {
            logger.info("Pending donation already exists for providerRef={}", providerRef)
            return existing
        }

        val donor = donorProfileRepository.findById(donorProfileId)
            .orElseThrow { NotFoundException("Donor profile not found: $donorProfileId") }
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }

        return donationRepository.save(
            Donation(
                donor = donor,
                campaign = campaign,
                amount = amount,
                providerRef = providerRef,
                sourceSite = sourceSite,
                donorFullName = identity.fullName,
                donorAddressLine1 = identity.addressLine1,
                donorAddressLine2 = identity.addressLine2,
                donorPostalCode = identity.postalCode,
                donorCity = identity.city,
                donorCountry = identity.country,
                donorBirthDate = identity.birthDate,
                donorBirthCity = identity.birthCity,
            )
        ).also { logger.info("Created pending donation id={} providerRef={}", it.id, providerRef) }
    }
}

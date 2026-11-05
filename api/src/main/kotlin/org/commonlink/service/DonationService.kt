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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Snapshot of donor identity fields captured at donation time for the Cerfa 2041-RD receipt.
 *
 * Every field except [birthDate] is stored on the [Donation] row — immutable fiscal truth
 * independent of later profile changes.
 *
 * [birthDate] is the exception: facultative on the widget form and **never persisted**. It travels
 * this far only because [FreezeScreeningDonationService] needs it to discard homonyms, and is
 * dropped once the screening has run.
 */
data class DonorIdentitySnapshot(
    val fullName: String,
    val addressLine1: String,
    val addressLine2: String?,
    val postalCode: String,
    val city: String,
    val country: String,
    val birthDate: LocalDate?,
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

    companion object {
        /** Tolerated clock difference between the payment provider and this server when dating a donation. */
        val CLOCK_SKEW: Duration = Duration.ofMinutes(5)
    }

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
     * @param paidAt Payment time reported by the provider's re-read; becomes the donation date when plausible
     *   (see [confirmDonation]).
     */
    @Transactional
    fun recordPayment(
        providerRef: String,
        donorProfileId: UUID,
        campaignId: UUID,
        amount: BigDecimal,
        paidAt: Instant? = null,
    ) {
        val existing = donationRepository.findByProviderRef(providerRef)
        if (existing != null) {
            if (existing.confirmedAt != null) {
                logger.info("Skipping already-confirmed donation providerRef={}", providerRef)
                return
            }
            confirmDonation(existing.id!!, paidAt)
            return
        }

        val donor = donorProfileRepository.findById(donorProfileId)
            .orElseThrow { NotFoundException("Donor profile not found: $donorProfileId") }
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }

        val donation = donationRepository.save(
            Donation(donor = donor, campaign = campaign, amount = amount, providerRef = providerRef)
        )
        confirmDonation(donation.id!!, paidAt)
    }

    /**
     * Marks a donation as confirmed and derives the donor wallet address.
     *
     * Publishes [DonationConfirmedEvent] after the transaction commits so that receipt
     * generation and on-chain enqueue happen asynchronously without blocking the caller.
     * Idempotent: if the donation is already confirmed the call is a no-op.
     *
     * [Donation.confirmedAt] is the donation date everywhere (fiscal receipt year, yearly filters, statistics), so it
     * takes the provider's [paidAt] rather than the time of this call, which may come later (late notification,
     * reconciler) — a payment made on 31 December keeps its year. An implausible [paidAt] (in the future, or before
     * the donation was created, beyond [CLOCK_SKEW]) or a missing one falls back to now.
     *
     * @param donationId UUID of the [Donation] to confirm.
     * @param paidAt Payment time reported by the provider, if known.
     * @throws NotFoundException if the donation is not found.
     */
    @Transactional
    fun confirmDonation(donationId: UUID, paidAt: Instant? = null) {
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

        donation.confirmedAt = donationDate(donation, paidAt)
        donationRepository.save(donation)
        creditCampaign(donation)

        publisher.publishEvent(DonationConfirmedEvent(donation.id!!))
        logger.info("Confirmed donation {} — receipt generation enqueued async", donation.id)
    }

    /** [paidAt] when plausible for [donation], else now (see [confirmDonation]). */
    private fun donationDate(donation: Donation, paidAt: Instant?): Instant {
        val now = Instant.now()
        if (paidAt == null) return now
        if (paidAt.isAfter(now.plus(CLOCK_SKEW)) || paidAt.isBefore(donation.createdAt.minus(CLOCK_SKEW))) {
            logger.warn(
                "Implausible payment time {} for donation {} (created {}): dated now", paidAt, donation.id, donation.createdAt,
            )
            return now
        }
        return minOf(paidAt, now)
    }

    /**
     * Adds [donation]'s amount to [org.commonlink.entity.Campaign.raised].
     *
     * This is the single writer of that column. It is read by every campaign projection
     * ([org.commonlink.dto.CampaignDto], [org.commonlink.dto.PublicWidgetDto]), by the collection-cap
     * check in [DonationCapService], and by
     * [org.commonlink.repository.CampaignMilestoneRepository.findNextMilestoneByAssociationId] —
     * all of which were silently reading a column nobody incremented before this.
     *
     * The increment is done in SQL ([CampaignRepository.addToRaised]) so two donations confirmed
     * concurrently on the same campaign cannot lose an increment. The guard on [Donation.confirmedAt]
     * in the caller keeps it idempotent.
     *
     * Note for callers of [confirmDonation]: that bulk update clears the persistence context, so any
     * entity read before it must be re-read afterwards rather than reused.
     */
    private fun creditCampaign(donation: Donation) {
        val campaignId = donation.campaign.id!!
        val updated = campaignRepository.addToRaised(campaignId, donation.amount)
        if (updated == 0) throw NotFoundException("Campaign not found: $campaignId")
        logger.debug("Campaign {} credited with {}", campaignId, donation.amount)
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
     * @param identity Fiscal identity snapshot required for the Cerfa 2041-RD receipt. Its
     *   [DonorIdentitySnapshot.birthDate] is not written: see the note on that field.
     * @param publicRef Opaque correlation id minted by the caller before the Mollie redirect;
     *   stored so the `/return` page can poll status by it. Null outside the public widget flow.
     */
    @Transactional
    fun initiatePendingDonation(
        providerRef: String,
        donorProfileId: UUID,
        campaignId: UUID,
        amount: BigDecimal,
        sourceSite: String?,
        identity: DonorIdentitySnapshot,
        publicRef: UUID? = null,
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
                // donorBirthDate / donorBirthCity are deliberately left unset: the widget collects
                // no birth city, and the birth date — facultative — is consumed by the freeze
                // screening then discarded. See DonorIdentitySnapshot and Donation.donorBirthDate.
                publicRef = publicRef,
            )
        ).also { logger.info("Created pending donation id={} providerRef={}", it.id, providerRef) }
    }
}

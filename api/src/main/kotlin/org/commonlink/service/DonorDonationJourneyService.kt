package org.commonlink.service

import org.commonlink.dto.DonorDonationJourneyDto
import org.commonlink.dto.JourneyStep
import org.commonlink.dto.JourneyStepDto
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.OnchainJobRepository
import org.commonlink.security.DonorReadScope
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/** Bound on how many of the donor's own donations are scanned to locate précédent/suivant navigation. */
private const val MAX_HISTORY_FOR_NAVIGATION = 1000

/**
 * Derives the 4-step journey of one donation, for the "parcours de votre don" timeline.
 *
 * All reads go through [DonorReadScope.assertOwnsDonation] first.
 */
@Service
class DonorDonationJourneyService(
    private val donorReadScope: DonorReadScope,
    private val donationAllocationService: DonationAllocationService,
    private val onchainJobRepository: OnchainJobRepository,
    private val donationRepository: DonationRepository,
) {

    /**
     * @param userId UUID of the authenticated user.
     * @throws org.commonlink.exception.NotFoundException if [donationId] does not exist.
     * @throws org.springframework.security.access.AccessDeniedException if [donationId] belongs to another donor.
     */
    @Transactional(readOnly = true)
    fun getJourney(userId: UUID, donationId: UUID): DonorDonationJourneyDto {
        val donorId = donorReadScope.resolve(userId).id!!
        val donation = donorReadScope.assertOwnsDonation(donorId, donationId)
        val campaignId = donation.campaign.id!!

        val recorded = onchainJobRepository.findByCorrelationKey("DONATION:$donationId")
            ?.status == OnchainJobStatus.DONE

        val allocation = donationAllocationService.allocateCampaign(campaignId)
            .firstOrNull { it.donationId == donationId }
        val spent = (allocation?.usedAmount ?: BigDecimal.ZERO) > BigDecimal.ZERO

        val impactReported = donation.campaign.status == CampaignStatus.COMPLETED

        val steps = listOf(
            // Only donations with confirmedAt != null ever reach this point (see DonorReadScope.assertOwnsDonation
            // → DonationRepository.findById, followed by the confirmedAt-filtered read paths above it).
            JourneyStepDto(JourneyStep.RECEIVED, reached = true, reachedAt = donation.confirmedAt),
            JourneyStepDto(JourneyStep.RECORDED, reached = recorded, reachedAt = null),
            JourneyStepDto(JourneyStep.SPENT, reached = spent, reachedAt = null),
            JourneyStepDto(JourneyStep.IMPACT_REPORTED, reached = impactReported, reachedAt = null),
        )

        val (previousDonationId, nextDonationId) = navigationNeighbours(donorId, donationId)

        return DonorDonationJourneyDto(
            donationId = donationId,
            steps = steps,
            previousDonationId = previousDonationId,
            nextDonationId = nextDonationId,
            usedAmount = allocation?.usedAmount ?: BigDecimal.ZERO,
            remainingAmount = allocation?.remainingAmount ?: donation.amount,
            fundedPayouts = allocation?.fundedPayouts ?: emptyList(),
        )
    }

    /**
     * Finds the donor's chronologically earlier ("précédent") and later ("suivant") donation
     * relative to [donationId], within the donor's [MAX_HISTORY_FOR_NAVIGATION] most recent
     * confirmed donations (newest first — donor volumes are tens, not thousands; see spec §2.3).
     */
    private fun navigationNeighbours(donorId: UUID, donationId: UUID): Pair<UUID?, UUID?> {
        val history = donationRepository
            .findByDonorIdFiltered(donorId, null, null, PageRequest.of(0, MAX_HISTORY_FOR_NAVIGATION))
            .content
        val index = history.indexOfFirst { it.id == donationId }
        if (index < 0) return null to null
        val previousDonationId = history.getOrNull(index + 1)?.id // older
        val nextDonationId = if (index > 0) history[index - 1].id else null // newer
        return previousDonationId to nextDonationId
    }
}

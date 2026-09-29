package org.commonlink.service

import org.commonlink.dto.DonorAssociationDto
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read-only service backing the donor's "My associations" page.
 *
 * Lists the associations the donor has actually funded, with what they gave, how many campaigns
 * they supported, and the association's published-payout record. Resolution goes through
 * [DonorReadScope]: the page takes no association id, so nothing outside the donor's own history
 * can be reached.
 */
@Service
class DonorAssociationService(
    private val donorReadScope: DonorReadScope,
    private val donationRepository: DonationRepository,
    private val payoutRepository: PayoutRepository,
) {

    /**
     * Returns the associations the donor supports, most funded first.
     *
     * @param userId UUID of the authenticated user.
     * @return one entry per association with at least one confirmed donation; empty when the donor
     *   has never given.
     * @throws org.commonlink.exception.UserNotFoundException if the user has no donor profile.
     */
    @Transactional(readOnly = true)
    fun listAssociations(userId: UUID): List<DonorAssociationDto> {
        val donorId = donorReadScope.resolve(userId).id!!
        val aggregates = donationRepository.findAssociationAggregatesByDonorId(donorId)
        if (aggregates.isEmpty()) return emptyList()

        // Rows come oldest first, so the last one seen per association is the category of the
        // campaign funded most recently — see findAssociationCategoriesByDonorId.
        val categories: Map<UUID, String?> = donationRepository.findAssociationCategoriesByDonorId(donorId)
            .associate { it.getAssociationId() to it.getCategory() }

        return aggregates.map { row ->
            DonorAssociationDto(
                associationId = row.getAssociationId(),
                name = row.getName(),
                category = categories[row.getAssociationId()],
                totalDonated = row.getTotalAmount(),
                // One count per association: bounded by the number of associations this donor funds.
                publishedPayoutCount = payoutRepository.countConfirmedByAssociationId(row.getAssociationId()),
                campaignCount = row.getCampaignCount().toInt(),
                lastDonationAt = row.getLastDonationAt(),
            )
        }
    }
}

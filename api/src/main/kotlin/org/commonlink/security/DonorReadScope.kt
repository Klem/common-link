package org.commonlink.security

import org.commonlink.entity.Donation
import org.commonlink.entity.DonorProfile
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Resolves the [DonorProfile] of the authenticated user and enforces its read scope.
 *
 * Every donor-side read goes through this component: a donor may only read a campaign they have
 * actually made a confirmed donation to, and may only read their own donations. The whole backend
 * is otherwise built on the mirror path (association → ownership); this guard is the single place
 * where the donor path is expressed, so that it cannot drift endpoint by endpoint.
 *
 * Denial semantics follow the wiring already in place in
 * [org.commonlink.exception.GlobalExceptionHandler]: a missing row is a 404
 * ([NotFoundException]), an existing row that belongs to somebody else is a 403
 * ([AccessDeniedException]). Ownership is always checked before any further existence check, so
 * that a probe never learns anything about a resource it is not entitled to.
 */
@Component
class DonorReadScope(
    private val donorProfileRepository: DonorProfileRepository,
    private val donationRepository: DonationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the donor profile attached to [userId].
     *
     * @param userId UUID of the authenticated user — never a profile id (see `principal.username`).
     * @return the donor profile owned by that user.
     * @throws UserNotFoundException if no donor profile is attached to [userId].
     */
    fun resolve(userId: UUID): DonorProfile =
        donorProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Donor profile not found for user $userId") }

    /**
     * Asserts that [donorId] has at least one confirmed donation on [campaignId].
     *
     * @param donorId id of the donor profile whose scope is being checked.
     * @param campaignId campaign the caller wants to read.
     * @throws AccessDeniedException if the donor has no confirmed donation on that campaign.
     */
    fun assertHasDonatedTo(donorId: UUID, campaignId: UUID) {
        if (!donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignId)) {
            log.warn("Donor {} denied read access to campaign {}: no confirmed donation", donorId, campaignId)
            throw AccessDeniedException("Campaign not in donor read scope")
        }
    }

    /**
     * Asserts that [donationId] belongs to [donorId] and returns it.
     *
     * @param donorId id of the donor profile whose scope is being checked.
     * @param donationId donation the caller wants to read.
     * @return the donation, guaranteed to belong to [donorId].
     * @throws NotFoundException if no donation carries that id.
     * @throws AccessDeniedException if the donation belongs to another donor.
     */
    fun assertOwnsDonation(donorId: UUID, donationId: UUID): Donation {
        val donation = donationRepository.findById(donationId)
            .orElseThrow { NotFoundException("Donation not found: $donationId") }
        if (donation.donor.id != donorId) {
            log.warn("Donor {} denied read access to donation {}: owned by another donor", donorId, donationId)
            throw AccessDeniedException("Donation not in donor read scope")
        }
        return donation
    }
}

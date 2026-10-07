package org.commonlink.service

import org.commonlink.dto.CampaignDonorDto
import org.commonlink.dto.DonationDto
import org.commonlink.dto.toDto
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.UUID

private const val ANONYMOUS_LABEL = "Anonyme"

/**
 * Read-only service exposing per-campaign donor aggregates and donation history.
 *
 * Anonymity rule: when [org.commonlink.entity.DonorProfile.anonymous] is true the donor's
 * [org.commonlink.entity.DonorProfile.displayName] is replaced with "Anonyme" in all responses.
 * The donorId (UUID) and providerRef (on-chain proof) are always returned.
 */
@Service
class DonorAggregateService(
    private val donationRepository: DonationRepository,
    private val campaignRepository: CampaignRepository,
    private val associationProfileRepository: AssociationProfileRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns a page of donor aggregates for [campaignId].
     *
     * [sort] accepts "amount" (default, total), "date" (lastDonationAt), "name" (display name as
     * shown to the association — anonymous donors sort as "Anonyme") or "count" (txCount).
     * Unknown values fall back to "amount".
     * [direction] accepts "asc" or "desc" (default); unknown values fall back to "desc".
     * Ties are broken by donorId so pagination stays stable.
     * [search] filters by donor display name; anonymous donors are excluded from search results.
     *
     * @throws NotFoundException if [campaignId] does not belong to [associationId].
     */
    fun listDonors(
        campaignId: UUID,
        userId: UUID,
        search: String?,
        sort: String?,
        direction: String?,
        page: Int,
        size: Int,
    ): Page<CampaignDonorDto> {
        val associationId = resolveAssociationId(userId)
        assertCampaignOwnership(campaignId, associationId)

        val pageable = PageRequest.of(page, size, donorSort(sort, direction))

        val rows = if (!search.isNullOrBlank()) {
            donationRepository.findDonorAggregatesByCampaignIdAndSearch(campaignId, search.trim(), pageable)
        } else {
            donationRepository.findDonorAggregatesByCampaignId(campaignId, pageable)
        }

        return rows.map { row ->
            CampaignDonorDto(
                donorId       = row.getDonorId(),
                displayName   = if (row.getAnonymous()) ANONYMOUS_LABEL else (row.getDisplayName() ?: ANONYMOUS_LABEL),
                totalAmount   = row.getTotalAmount(),
                txCount       = row.getTxCount(),
                lastDonationAt = row.getLastDonationAt(),
            )
        }
    }

    /**
     * Returns a page of donations made by [donorId] on [campaignId], newest first.
     *
     * @throws NotFoundException if [campaignId] does not belong to [associationId].
     */
    fun listDonorDonations(
        campaignId: UUID,
        donorId: UUID,
        userId: UUID,
        page: Int,
        size: Int,
    ): Page<DonationDto> {
        val associationId = resolveAssociationId(userId)
        assertCampaignOwnership(campaignId, associationId)
        val pageable = PageRequest.of(page, size)
        return donationRepository.findByDonorIdAndCampaignId(donorId, campaignId, pageable).map { it.toDto() }
    }

    /**
     * Returns a single donation scoped to [campaignId].
     *
     * @throws NotFoundException if [donationId] does not exist or does not belong to [campaignId] / [associationId].
     */
    fun getDonation(campaignId: UUID, donationId: UUID, userId: UUID): DonationDto {
        val associationId = resolveAssociationId(userId)
        assertCampaignOwnership(campaignId, associationId)
        val donation = donationRepository.findById(donationId)
            .orElseThrow { NotFoundException("Donation not found: $donationId") }
        if (donation.campaign.id != campaignId) throw NotFoundException("Donation not found: $donationId")
        return donation.toDto()
    }

    /** Maps the API [sort]/[direction] params to a DB sort on the aggregate query aliases, donorId as tiebreaker. */
    private fun donorSort(sort: String?, direction: String?): Sort {
        val dir = if (direction.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val property = when (sort) {
            "date"  -> "lastDonationAt"
            "name"  -> "sortName"
            "count" -> "txCount"
            else    -> "totalAmount"
        }
        return Sort.by(dir, property).and(Sort.by(Sort.Direction.ASC, "donorId"))
    }

    private fun assertCampaignOwnership(campaignId: UUID, associationId: UUID) {
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }
        if (campaign.association.id != associationId) throw NotFoundException("Campaign not found: $campaignId")
        log.debug("Ownership verified: campaign {} → association {}", campaignId, associationId)
    }

    private fun resolveAssociationId(userId: UUID): UUID =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!
}

package org.commonlink.service

import org.commonlink.dto.BudgetVarianceDto
import org.commonlink.dto.SectionVarianceDto
import org.commonlink.dto.TotalsVarianceDto
import org.commonlink.entity.BudgetSide
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignBudgetSectionRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/**
 * Computes budget variance (prévu vs réalisé) for a campaign.
 *
 * Charges mapping: payout.typeCode prefix (e.g. "60" from "60-mat") → section.code.
 * Produits mapping: donation.typeCode (e.g. "74") → section.code.
 * Only CONFIRMED payouts and donations with confirmedAt != null count as réalisé.
 */
@Service
class ReportingService(
    private val campaignRepository: CampaignRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val sectionRepository: CampaignBudgetSectionRepository,
    private val payoutRepository: PayoutRepository,
    private val donationRepository: DonationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the budget variance report for [campaignId].
     *
     * @throws UserNotFoundException if [userId] does not match an association profile.
     * @throws NotFoundException if [campaignId] does not exist or does not belong to the association.
     */
    fun getVariance(campaignId: UUID, userId: UUID): BudgetVarianceDto {
        val associationId = resolveAssociationId(userId)
        assertCampaignOwnership(campaignId, associationId)

        val sections = sectionRepository.findAllWithItemsByCampaignId(campaignId)

        // typeCode prefix → realised amount (payouts: "60-mat" → "60"; donations: "74" → "74")
        val actualCharges = buildActualMap(
            payoutRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId)
        )
        val actualProduits = buildActualMap(
            donationRepository.sumConfirmedAmountsByCampaignIdGroupedByTypeCode(campaignId)
        )

        val chargeSections = sections
            .filter { it.side == BudgetSide.EXPENSE }
            .map { sec ->
                val planned = sec.items.sumOf { it.amount }
                val actual = actualCharges.getOrDefault(sec.code, BigDecimal.ZERO)
                SectionVarianceDto(
                    sectionCode = sec.code,
                    sectionName = sec.name,
                    planned = planned,
                    actual = actual,
                    variance = actual - planned,
                )
            }

        val produitSections = sections
            .filter { it.side == BudgetSide.REVENUE }
            .map { sec ->
                val planned = sec.items.sumOf { it.amount }
                val actual = actualProduits.getOrDefault(sec.code, BigDecimal.ZERO)
                SectionVarianceDto(
                    sectionCode = sec.code,
                    sectionName = sec.name,
                    planned = planned,
                    actual = actual,
                    variance = actual - planned,
                )
            }

        val totals = TotalsVarianceDto(
            totalPlannedCharges = chargeSections.sumOf { it.planned },
            totalActualCharges = chargeSections.sumOf { it.actual },
            totalPlannedProduits = produitSections.sumOf { it.planned },
            totalActualProduits = produitSections.sumOf { it.actual },
        )

        log.debug(
            "Budget variance computed for campaign={}: {} charge sections, {} produit sections",
            campaignId, chargeSections.size, produitSections.size,
        )
        return BudgetVarianceDto(charges = chargeSections, produits = produitSections, totals = totals)
    }

    /**
     * Converts a grouped query result (typeCode, sum) to a map keyed by typeCode prefix.
     * "60-mat" → "60", "74" → "74" (substringBefore('-') returns the full string when no dash).
     */
    private fun buildActualMap(rows: List<Array<Any>>): Map<String, BigDecimal> =
        rows.associate { row ->
            val prefix = (row[0] as String).substringBefore('-')
            val amount = (row[1] as Number).let { BigDecimal(it.toString()) }
            prefix to amount
        }

    private fun resolveAssociationId(userId: UUID): UUID =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("User $userId not found") }
            .id!!

    private fun assertCampaignOwnership(campaignId: UUID, associationId: UUID) {
        campaignRepository.findById(campaignId)
            .filter { it.association.id == associationId }
            .orElseThrow { NotFoundException("Campaign $campaignId not found") }
    }
}

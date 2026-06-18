package org.commonlink.repository

import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.UUID

interface PayoutRepository : JpaRepository<Payout, UUID> {

    fun findByCampaignIdOrderByCreatedAtDesc(campaignId: UUID, pageable: Pageable): Page<Payout>

    fun findByCampaignIdAndIdAndCampaignAssociationId(
        campaignId: UUID,
        id: UUID,
        associationId: UUID,
    ): Payout?

    /** Total count of payouts for a campaign (all statuses). */
    fun countByCampaignId(campaignId: UUID): Long

    /** Count of payouts with a given status. */
    fun countByCampaignIdAndStatus(campaignId: UUID, status: PayoutStatus): Long

    /**
     * Sum of [Payout.amount] for payouts with [status] on [campaignId].
     * Returns null when no rows match; treat as zero.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payout p
        WHERE p.campaign.id = :campaignId
          AND p.status      = :status
    """)
    fun sumAmountByCampaignIdAndStatus(
        @Param("campaignId") campaignId: UUID,
        @Param("status") status: PayoutStatus,
    ): BigDecimal?

    /**
     * Returns confirmed payout amounts grouped by [Payout.typeCode] for budget variance reporting.
     * Each element is [typeCode, sum].
     */
    @Query("""
        SELECT p.typeCode, COALESCE(SUM(p.amount), 0)
        FROM Payout p
        WHERE p.campaign.id = :campaignId
          AND p.status = org.commonlink.entity.PayoutStatus.CONFIRMED
        GROUP BY p.typeCode
    """)
    fun sumConfirmedAmountsByCampaignIdGroupedByTypeCode(
        @Param("campaignId") campaignId: UUID,
    ): List<Array<Any>>
}

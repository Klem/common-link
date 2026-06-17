package org.commonlink.repository

import org.commonlink.entity.Donation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Projection for monthly fundraising aggregates returned by the dashboard query. */
interface MonthlyAmountRow {
    fun getYear(): Number
    fun getMonth(): Number
    fun getAmount(): BigDecimal
}

interface DonationRepository : JpaRepository<Donation, UUID> {

    fun findByProviderRef(providerRef: String): Donation?

    /**
     * Sum of confirmed donation amounts across all LIVE campaigns for the given association.
     * Returns null when there are no matching rows; callers should treat null as zero.
     */
    @Query("""
        SELECT COALESCE(SUM(d.amount), 0)
        FROM Donation d
        JOIN d.campaign c
        WHERE c.association.id = :associationId
          AND c.status = org.commonlink.entity.CampaignStatus.LIVE
          AND d.confirmedAt IS NOT NULL
    """)
    fun sumConfirmedAmountByAssociationId(@Param("associationId") associationId: UUID): BigDecimal?

    /**
     * Count of distinct donors with confirmed donations on LIVE campaigns for the given association.
     */
    @Query("""
        SELECT COUNT(DISTINCT d.donor.id)
        FROM Donation d
        JOIN d.campaign c
        WHERE c.association.id = :associationId
          AND c.status = org.commonlink.entity.CampaignStatus.LIVE
          AND d.confirmedAt IS NOT NULL
    """)
    fun countDistinctDonorsByAssociationId(@Param("associationId") associationId: UUID): Long

    /**
     * Monthly confirmed donation totals for the association since [since], ordered by year/month ascending.
     * Months with no donations are absent from the result; the service fills them with zero.
     *
     * Uses HQL `extract()` instead of native SQL to remain compatible with both PostgreSQL and H2
     * (the test slice runs on H2 which doesn't support PostgreSQL-specific `date_trunc`/`to_char`).
     */
    @Query("""
        SELECT extract(year  from d.confirmedAt) AS year,
               extract(month from d.confirmedAt) AS month,
               SUM(d.amount)                     AS amount
        FROM   Donation d
        JOIN   d.campaign c
        WHERE  c.association.id = :associationId
          AND  d.confirmedAt IS NOT NULL
          AND  d.confirmedAt >= :since
        GROUP BY extract(year from d.confirmedAt), extract(month from d.confirmedAt)
        ORDER BY extract(year from d.confirmedAt) ASC, extract(month from d.confirmedAt) ASC
    """)
    fun findMonthlyAmountsByAssociationId(
        @Param("associationId") associationId: UUID,
        @Param("since") since: Instant,
    ): List<MonthlyAmountRow>

    /**
     * Most recent confirmed donations for the given association, newest first.
     * Donor is eagerly fetched to avoid N+1 when building the activity label.
     * Use [pageable] to cap the result set (e.g. `PageRequest.of(0, 10)`).
     */
    @Query("""
        SELECT d FROM Donation d
        JOIN FETCH d.donor
        JOIN d.campaign c
        WHERE c.association.id = :associationId
          AND d.confirmedAt IS NOT NULL
        ORDER BY d.confirmedAt DESC
    """)
    fun findRecentByAssociationId(
        @Param("associationId") associationId: UUID,
        pageable: Pageable,
    ): List<Donation>

    /**
     * Sum of confirmed donation amounts for a single campaign.
     * Returns null when no confirmed donations exist; callers should treat null as zero.
     */
    @Query("""
        SELECT COALESCE(SUM(d.amount), 0)
        FROM Donation d
        WHERE d.campaign.id = :campaignId
          AND d.confirmedAt IS NOT NULL
    """)
    fun sumConfirmedAmountByCampaignId(@Param("campaignId") campaignId: UUID): BigDecimal?

    // ── Per-campaign donor aggregates (Step 6) ─────────────────────────────

    /**
     * Aggregate view returned by per-campaign donor queries.
     * [getDisplayName] may be null if the donor has not set a display name.
     * [getAnonymous] must be checked by the service before exposing [getDisplayName].
     */
    interface DonorAggregateRow {
        fun getDonorId(): UUID
        fun getDisplayName(): String?
        fun getAnonymous(): Boolean
        fun getTotalAmount(): BigDecimal
        fun getTxCount(): Long
        fun getLastDonationAt(): Instant?
    }

    /**
     * Donor aggregates (sum / count / last date) for all confirmed donations on [campaignId].
     * Grouped and sorted by DB — no in-memory sort.
     */
    @Query(
        value = """
            SELECT d.donor.id         AS donorId,
                   d.donor.displayName AS displayName,
                   d.donor.anonymous   AS anonymous,
                   SUM(d.amount)       AS totalAmount,
                   COUNT(d)            AS txCount,
                   MAX(d.confirmedAt)  AS lastDonationAt
            FROM Donation d
            WHERE d.campaign.id = :campaignId
              AND d.confirmedAt IS NOT NULL
            GROUP BY d.donor.id, d.donor.displayName, d.donor.anonymous
        """,
        countQuery = """
            SELECT COUNT(DISTINCT d.donor.id)
            FROM Donation d
            WHERE d.campaign.id = :campaignId
              AND d.confirmedAt IS NOT NULL
        """,
    )
    fun findDonorAggregatesByCampaignId(
        @Param("campaignId") campaignId: UUID,
        pageable: Pageable,
    ): Page<DonorAggregateRow>

    /**
     * Like [findDonorAggregatesByCampaignId] but restricted to non-anonymous donors whose
     * display name contains [name] (case-insensitive).
     * Anonymous donors are excluded from search results regardless of their stored display name.
     */
    @Query(
        value = """
            SELECT d.donor.id         AS donorId,
                   d.donor.displayName AS displayName,
                   d.donor.anonymous   AS anonymous,
                   SUM(d.amount)       AS totalAmount,
                   COUNT(d)            AS txCount,
                   MAX(d.confirmedAt)  AS lastDonationAt
            FROM Donation d
            WHERE d.campaign.id = :campaignId
              AND d.confirmedAt IS NOT NULL
              AND d.donor.anonymous = false
              AND LOWER(d.donor.displayName) LIKE LOWER(CONCAT('%', :name, '%'))
            GROUP BY d.donor.id, d.donor.displayName, d.donor.anonymous
        """,
        countQuery = """
            SELECT COUNT(DISTINCT d.donor.id)
            FROM Donation d
            WHERE d.campaign.id = :campaignId
              AND d.confirmedAt IS NOT NULL
              AND d.donor.anonymous = false
              AND LOWER(d.donor.displayName) LIKE LOWER(CONCAT('%', :name, '%'))
        """,
    )
    fun findDonorAggregatesByCampaignIdAndSearch(
        @Param("campaignId") campaignId: UUID,
        @Param("name") name: String,
        pageable: Pageable,
    ): Page<DonorAggregateRow>

    /**
     * Confirmed donations for a specific donor on a campaign, newest first.
     * Use [pageable] to control page size and offset.
     */
    @Query("""
        SELECT d FROM Donation d
        WHERE d.donor.id = :donorId
          AND d.campaign.id = :campaignId
        ORDER BY d.confirmedAt DESC
    """)
    fun findByDonorIdAndCampaignId(
        @Param("donorId") donorId: UUID,
        @Param("campaignId") campaignId: UUID,
        pageable: Pageable,
    ): Page<Donation>
}

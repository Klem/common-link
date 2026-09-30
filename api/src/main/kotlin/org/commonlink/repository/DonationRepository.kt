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
     * Returns confirmed donations that have no matching RECORD_DONATION outbox job.
     *
     * Used by the receipt reconciler to recover from failures in the async event listener.
     * Each row's correlationKey pattern is `DONATION:<donationId>`.
     */
    @Query("""
        SELECT d FROM Donation d
        WHERE d.confirmedAt IS NOT NULL
          AND NOT EXISTS (
            SELECT j FROM OnchainJob j
            WHERE j.correlationKey = concat('DONATION:', str(d.id))
          )
    """)
    fun findConfirmedWithoutOnchainJob(): List<Donation>

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

    /**
     * Sum of amounts held by payment sessions still in flight on a campaign: donations created after
     * [since] whose payment has not been confirmed.
     *
     * Backs the collection-cap reservation (see
     * [org.commonlink.service.PublicWidgetService.createDonation]): without it, two donors checking
     * out at the same time would each see the full remaining capacity and collectively overshoot.
     * There is no reservation column — a pending row *is* the reservation, and [Donation.createdAt]
     * gives it its lifetime.
     *
     * Rows older than [since] are ignored: an abandoned checkout must not hold capacity for ever.
     * Returns null when no matching rows exist; callers should treat null as zero.
     */
    /**
     * Number of payment sessions still in flight on a campaign — the row count matching
     * [sumPendingAmountByCampaignIdSince].
     *
     * Backs the pending-session ceiling in [org.commonlink.service.DonationCapService]: the amount
     * alone cannot tell a busy campaign from a campaign whose capacity is being held hostage by an
     * unauthenticated caller (security audit 2026-08-20, M6).
     */
    @Query("""
        SELECT COUNT(d)
        FROM Donation d
        WHERE d.campaign.id = :campaignId
          AND d.confirmedAt IS NULL
          AND d.createdAt >= :since
    """)
    fun countPendingByCampaignIdSince(
        @Param("campaignId") campaignId: UUID,
        @Param("since") since: Instant,
    ): Long

    @Query("""
        SELECT COALESCE(SUM(d.amount), 0)
        FROM Donation d
        WHERE d.campaign.id = :campaignId
          AND d.confirmedAt IS NULL
          AND d.createdAt >= :since
    """)
    fun sumPendingAmountByCampaignIdSince(
        @Param("campaignId") campaignId: UUID,
        @Param("since") since: Instant,
    ): BigDecimal?

    /**
     * Returns confirmed donation amounts grouped by [Donation.typeCode] for budget variance reporting.
     * Each element is [typeCode, sum].
     */
    @Query("""
        SELECT d.typeCode, COALESCE(SUM(d.amount), 0)
        FROM Donation d
        WHERE d.campaign.id = :campaignId
          AND d.confirmedAt IS NOT NULL
        GROUP BY d.typeCode
    """)
    fun sumConfirmedAmountsByCampaignIdGroupedByTypeCode(
        @Param("campaignId") campaignId: UUID,
    ): List<Array<Any>>

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

    // ── Donor dashboard (Sprint 1) ────────────────────────────────────────

    /** Projection for the association selector of the donation history filter. */
    interface AssociationOptionRow {
        fun getId(): UUID
        fun getName(): String
    }

    /** Per-association aggregates shown on the donor's "My associations" page. */
    interface DonorAssociationRow {
        fun getAssociationId(): UUID
        fun getName(): String
        fun getTotalAmount(): BigDecimal
        fun getCampaignCount(): Long
        fun getLastDonationAt(): Instant?
    }

    /** One confirmed donation reduced to the campaign category of the association it funded. */
    interface AssociationCategoryRow {
        fun getAssociationId(): UUID
        fun getCategory(): String?
    }

    /** One campaign the donor has funded with at least one confirmed donation -- feeds the impact gallery. */
    interface DonorCampaignRow {
        fun getCampaignId(): UUID
        fun getCampaignName(): String
        fun getCampaignEmoji(): String
        fun getCategory(): String?
        fun getImpactGoals(): String?
        fun getAssociationId(): UUID
        fun getAssociationName(): String
    }

    /** A receipted donation reduced to what the estimated tax reduction needs: who, how much, when. */
    interface ReceiptedDonationRow {
        fun getAssociationId(): UUID
        fun getAmount(): BigDecimal
        fun getConfirmedAt(): Instant
    }

    /**
     * A receipted donation of one calendar year, with the fields a fiscal recap PDF must show —
     * association name and the receipt number the donor can cross-check against the per-donation
     * PDF already downloadable via [org.commonlink.service.DonorDashboardService.getReceipt].
     */
    interface ReceiptedDonationDetailRow {
        fun getAssociationId(): UUID
        fun getAssociationName(): String
        fun getAmount(): BigDecimal
        fun getConfirmedAt(): Instant
        fun getReceiptNumber(): String
    }

    /**
     * Paginated confirmed-donation history of a donor, newest first, with both optional filters
     * applied in the query.
     *
     * [associationId] and [year] are optional: a null value disables that filter. The campaign and
     * its association are JOIN FETCHed because every row of the history renders both — without it
     * each row would trigger two extra selects.
     *
     * The count query is supplied explicitly: Spring Data cannot derive one from a query carrying
     * fetch joins.
     *
     * @param pageable page and size only — the ordering is fixed by the query.
     */
    @Query(
        value = """
            SELECT d FROM Donation d
            JOIN FETCH d.campaign c
            JOIN FETCH c.association a
            WHERE d.donor.id  = :donorId
              AND d.confirmedAt IS NOT NULL
              AND (:associationId IS NULL OR a.id = :associationId)
              AND (:year          IS NULL OR extract(year from d.confirmedAt) = :year)
            ORDER BY d.confirmedAt DESC
        """,
        countQuery = """
            SELECT COUNT(d) FROM Donation d
            JOIN d.campaign c
            JOIN c.association a
            WHERE d.donor.id  = :donorId
              AND d.confirmedAt IS NOT NULL
              AND (:associationId IS NULL OR a.id = :associationId)
              AND (:year          IS NULL OR extract(year from d.confirmedAt) = :year)
        """,
    )
    fun findByDonorIdFiltered(
        @Param("donorId") donorId: UUID,
        @Param("associationId") associationId: UUID?,
        @Param("year") year: Int?,
        pageable: Pageable,
    ): Page<Donation>

    /**
     * Total amount confirmed by a donor, all campaigns and associations combined.
     * Returns null when the donor has no confirmed donation; callers treat null as zero.
     */
    @Query("""
        SELECT COALESCE(SUM(d.amount), 0)
        FROM Donation d
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
    """)
    fun sumConfirmedAmountByDonorId(@Param("donorId") donorId: UUID): BigDecimal?

    /** Number of confirmed donations made by a donor. */
    @Query("""
        SELECT COUNT(d)
        FROM Donation d
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
    """)
    fun countConfirmedByDonorId(@Param("donorId") donorId: UUID): Long

    /** Number of distinct associations a donor has funded with at least one confirmed donation. */
    @Query("""
        SELECT COUNT(DISTINCT c.association.id)
        FROM Donation d
        JOIN d.campaign c
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
    """)
    fun countDistinctAssociationsByDonorId(@Param("donorId") donorId: UUID): Long

    /**
     * Years in which the donor has at least one confirmed donation, most recent first.
     * Feeds the year selector of the history filter.
     *
     * Uses HQL `extract()` rather than native SQL so the query runs unchanged on PostgreSQL and H2.
     */
    @Query("""
        SELECT DISTINCT extract(year from d.confirmedAt)
        FROM Donation d
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
        ORDER BY extract(year from d.confirmedAt) DESC
    """)
    fun findDistinctYearsByDonorId(@Param("donorId") donorId: UUID): List<Int>

    /** Associations the donor has funded, alphabetically — feeds the association selector. */
    @Query("""
        SELECT DISTINCT a.id AS id, a.name AS name
        FROM Donation d
        JOIN d.campaign c
        JOIN c.association a
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
        ORDER BY a.name ASC
    """)
    fun findDistinctAssociationsByDonorId(@Param("donorId") donorId: UUID): List<AssociationOptionRow>

    /**
     * One row per association funded by the donor: cumulated amount, number of distinct campaigns
     * supported, and date of the last confirmed donation.
     *
     * The category is **not** part of this projection: it is carried by [org.commonlink.entity.Campaign],
     * not by the association, so there is no such thing as a single association-level category.
     * See [findAssociationCategoriesByDonorId].
     */
    @Query("""
        SELECT a.id               AS associationId,
               a.name             AS name,
               SUM(d.amount)      AS totalAmount,
               COUNT(DISTINCT c.id) AS campaignCount,
               MAX(d.confirmedAt) AS lastDonationAt
        FROM Donation d
        JOIN d.campaign c
        JOIN c.association a
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
        GROUP BY a.id, a.name
        ORDER BY SUM(d.amount) DESC
    """)
    fun findAssociationAggregatesByDonorId(@Param("donorId") donorId: UUID): List<DonorAssociationRow>

    /**
     * Association id and campaign category for each confirmed donation of the donor, **oldest first**.
     *
     * The ordering is the contract: the caller keeps the last row seen per association, which is the
     * category of the most recently funded campaign. Showing the category of the campaign the donor
     * actually supported last is the only association-level category that means anything, since the
     * attribute belongs to the campaign.
     */
    @Query("""
        SELECT c.association.id AS associationId,
               c.category       AS category
        FROM Donation d
        JOIN d.campaign c
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
        ORDER BY d.confirmedAt ASC
    """)
    fun findAssociationCategoriesByDonorId(@Param("donorId") donorId: UUID): List<AssociationCategoryRow>

    /**
     * Confirmed donations of the donor that have a generated [org.commonlink.entity.DonationReceipt],
     * reduced to beneficiary, amount and date.
     *
     * Only these donations contribute to the estimated tax reduction: the Cerfa receipt *is* the
     * instrument of the reduction, and no receipt is issued without an active fiscal mandate.
     * Counting a receiptless donation would show a non-zero reduction next to a row the donor can
     * see carries no receipt.
     */
    @Query("""
        SELECT c.association.id AS associationId,
               d.amount         AS amount,
               d.confirmedAt    AS confirmedAt
        FROM Donation d
        JOIN d.campaign c
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
          AND EXISTS (SELECT r.id FROM DonationReceipt r WHERE r.donation = d)
    """)
    fun findReceiptedRowsByDonorId(@Param("donorId") donorId: UUID): List<ReceiptedDonationRow>

    /**
     * Receipted donations of a donor, with the fields the annual fiscal recap PDF needs. Same
     * "has a receipt" gate as [findReceiptedRowsByDonorId].
     *
     * Deliberately not filtered by year in SQL: `extract(year from ...)` would group by the
     * database session's timezone, while [org.commonlink.service.DonorReceiptsService] groups by
     * the Paris-zone year (same rule as [org.commonlink.service.DonorDashboardService]'s estimate)
     * — the two would silently disagree on donations made within an hour of a new year. The
     * caller filters to one Paris-zone year in memory instead, same bounded per-donor volume as
     * [findReceiptedRowsByDonorId].
     */
    @Query("""
        SELECT c.association.id   AS associationId,
               c.association.name AS associationName,
               d.amount           AS amount,
               d.confirmedAt      AS confirmedAt,
               r.receiptNumber    AS receiptNumber
        FROM Donation d
        JOIN d.campaign c
        JOIN DonationReceipt r ON r.donation = d
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
        ORDER BY d.confirmedAt ASC
    """)
    fun findReceiptedDetailRowsByDonorId(@Param("donorId") donorId: UUID): List<ReceiptedDonationDetailRow>

    /**
     * One row per campaign the donor has funded with at least one confirmed donation -- feeds the
     * "Impact de mes dons" gallery. Distinct from [findAssociationCategoriesByDonorId] (grouped by
     * association): the gallery is per-campaign, since impact goals and the story both belong to
     * the campaign, not the association.
     */
    @Query("""
        SELECT DISTINCT c.id AS campaignId, c.name AS campaignName, c.emoji AS campaignEmoji,
               c.category AS category, c.impactGoals AS impactGoals,
               a.id AS associationId, a.name AS associationName
        FROM Donation d
        JOIN d.campaign c
        JOIN c.association a
        WHERE d.donor.id = :donorId
          AND d.confirmedAt IS NOT NULL
        ORDER BY c.name ASC
    """)
    fun findDistinctCampaignsByDonorId(@Param("donorId") donorId: UUID): List<DonorCampaignRow>

    // ── Donor read scope (Sprint 1 — donor dashboard) ─────────────────────

    /**
     * True when [donorId] has at least one **confirmed** donation on [campaignId].
     *
     * Backs [org.commonlink.security.DonorReadScope.assertHasDonatedTo]: a donor may only read a
     * campaign they have actually funded. A pending payment grants nothing — the row exists but the
     * money never arrived.
     */
    @Query("""
        SELECT COUNT(d) > 0
        FROM Donation d
        WHERE d.donor.id    = :donorId
          AND d.campaign.id = :campaignId
          AND d.confirmedAt IS NOT NULL
    """)
    fun existsConfirmedByDonorIdAndCampaignId(
        @Param("donorId") donorId: UUID,
        @Param("campaignId") campaignId: UUID,
    ): Boolean

    /** Looks up a donation by the opaque [Donation.publicRef] handed to the donor on the Mollie redirect URL. */
    fun findByPublicRef(publicRef: UUID): Donation?

    /**
     * Donations still unconfirmed [threshold] after creation — candidates for
     * [org.commonlink.service.MolliePaymentReconciler]: either the Mollie webhook was never
     * delivered, or it was delivered and failed processing. `providerRef` is always set by this
     * point ([org.commonlink.service.DonationService.initiatePendingDonation] only runs after
     * the Mollie payment itself was created), so no null-check is needed here.
     */
    @Query("""
        SELECT d FROM Donation d
        WHERE d.confirmedAt IS NULL
          AND d.createdAt < :threshold
    """)
    fun findStalePending(@Param("threshold") threshold: Instant): List<Donation>

    // ── Donor dashboard (Sprint 2 — allocation & campaign report) ─────────

    /**
     * All confirmed donations of a campaign, oldest first — FIFO input for
     * [org.commonlink.service.DonationAllocationService], all donors combined.
     */
    fun findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAsc(campaignId: UUID): List<Donation>

    /**
     * This donor's total confirmed amount on one campaign — for the campaign report's "votre
     * contribution". Returns null when the donor has no confirmed donation on that campaign;
     * callers treat null as zero.
     */
    @Query("""
        SELECT COALESCE(SUM(d.amount), 0)
        FROM Donation d
        WHERE d.donor.id    = :donorId
          AND d.campaign.id = :campaignId
          AND d.confirmedAt IS NOT NULL
    """)
    fun sumConfirmedAmountByDonorIdAndCampaignId(
        @Param("donorId") donorId: UUID,
        @Param("campaignId") campaignId: UUID,
    ): BigDecimal?
}

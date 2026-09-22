package org.commonlink.repository

import jakarta.persistence.LockModeType
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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

    /** All payouts for a given payee, scoped to the owning association, newest first. */
    fun findByPayeeIdAndPayeeAssociationIdOrderByCreatedAtDesc(
        payeeId: UUID,
        associationId: UUID,
    ): List<Payout>

    /** True if the payee has at least one payout (any status) in the given association. */
    fun existsByPayeeIdAndPayeeAssociationId(payeeId: UUID, associationId: UUID): Boolean

    /** Distinct payee IDs that have at least one payout in the given association — for bulk listing. */
    @Query("SELECT DISTINCT p.payee.id FROM Payout p WHERE p.payee.association.id = :associationId")
    fun findDistinctPayeeIdsByAssociationId(@Param("associationId") associationId: UUID): Set<UUID>

    /**
     * Sum of amounts for payouts still PENDING but whose Bridge transfer is already engaged (a
     * non-null [Payout.bridgeStatus], set inside the locked confirm transaction before Bridge is
     * called).
     *
     * These amounts must be treated as spent: the association may already have authorised the
     * transfer at its bank even though this row has not yet been promoted to CONFIRMED. Without
     * this, two concurrent confirmations could each pass the balance check and both be authorised.
     *
     * Returns null when no rows match; treat as zero.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payout p
        WHERE p.campaign.id     = :campaignId
          AND p.status          = org.commonlink.entity.PayoutStatus.PENDING
          AND p.bridgeStatus IS NOT NULL
    """)
    fun sumInFlightAmountByCampaignId(@Param("campaignId") campaignId: UUID): BigDecimal?

    /**
     * Everything webhook routing needs of a payout, and deliberately nothing more.
     *
     * Returning the entity here loaded it into the request-scoped persistence context
     * (`open-in-view: true`) *before* any transaction. The transactional step that follows then
     * joined that context, and JPA answered its locked read from the identity map instead of the
     * database — so the row lock was taken while the state examined was the one read before the
     * concurrent thread committed. On 2026-09-22 at 13:50:27Z that made two notifications both
     * settle the same payout, the second one's "already CONFIRMED" guard reading a stale PENDING.
     * A projection cannot become managed, so the locked read inside the transaction is the first
     * load of that row and is necessarily fresh.
     */
    interface PayoutRouting {
        val id: UUID
        val bridgePaymentLinkId: String?
    }

    /** Routes a notification carrying a Bridge payment-link id. */
    @Query(
        """
        SELECT p.id AS id, p.bridgePaymentLinkId AS bridgePaymentLinkId
        FROM Payout p WHERE p.bridgePaymentLinkId = :bridgePaymentLinkId
        """,
    )
    fun findRoutingByBridgePaymentLinkId(@Param("bridgePaymentLinkId") bridgePaymentLinkId: String): PayoutRouting?

    /** Routes a notification that carried only `client_reference`, i.e. the payout id. */
    @Query("SELECT p.id AS id, p.bridgePaymentLinkId AS bridgePaymentLinkId FROM Payout p WHERE p.id = :id")
    fun findRoutingById(@Param("id") id: UUID): PayoutRouting?

    /**
     * Loads a payout holding a pessimistic write lock on its row.
     *
     * Only ever call this on a payout that is **not** already in the persistence context, or the
     * lock is taken while the state examined comes from the identity map: see [PayoutRouting].
     *
     * Bridge delivers its notifications concurrently — `payment.transaction.updated` and
     * `payment.link.updated` for one settlement arrived 7 ms apart on 2026-09-22 — so two threads
     * reached [org.commonlink.service.PayoutConfirmer.finaliseSettled] before either had committed,
     * both read a payout that was not yet CONFIRMED, and both enqueued the on-chain job. The second
     * insert hit the `onchain_jobs_correlation_key_key` unique constraint and the webhook answered
     * 502 with a technical alert, on a settlement that had in fact succeeded.
     *
     * Every webhook-driven transition takes this lock, not settlement alone: each one guards itself
     * by reading [Payout.status] first, and an unlocked read makes every one of those guards a
     * race. The costly variant is not the duplicate job but a stale reader stamping `FAILED` or
     * `PDNG` over a payout already CONFIRMED, whose on-chain attestation is public and final.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payout p WHERE p.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Payout?
}

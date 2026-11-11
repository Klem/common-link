package org.commonlink.repository

import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoDonationFunds
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Cash-out tracking rows of HelloAsso donations, keyed by donation id. */
interface HelloAssoDonationFundsRepository : JpaRepository<HelloAssoDonationFunds, UUID> {

    /**
     * Rows still to track (not [FundsState.AVAILABLE] nor [FundsState.WITHDRAWN]), with their donation and campaign
     * loaded so the caller groups them by association (`donation.campaign.association.id`) without another query.
     */
    @Query("""
        SELECT f FROM HelloAssoDonationFunds f
        JOIN FETCH f.donation d
        JOIN FETCH d.campaign c
        WHERE f.fundsState NOT IN (org.commonlink.entity.FundsState.AVAILABLE, org.commonlink.entity.FundsState.WITHDRAWN)
        ORDER BY f.createdAt, f.donationId
    """)
    fun findTrackable(): List<HelloAssoDonationFunds>

    /** Rows still to track for one association; see [findTrackable]. */
    @Query("""
        SELECT f FROM HelloAssoDonationFunds f
        JOIN FETCH f.donation d
        JOIN FETCH d.campaign c
        WHERE c.association.id = :associationId
          AND f.fundsState NOT IN (org.commonlink.entity.FundsState.AVAILABLE, org.commonlink.entity.FundsState.WITHDRAWN)
        ORDER BY f.createdAt, f.donationId
    """)
    fun findTrackableByAssociationId(@Param("associationId") associationId: UUID): List<HelloAssoDonationFunds>

    /** Whether any row is still to track: the guard that keeps the tracking pass from calling HelloAsso for nothing. */
    @Query("""
        SELECT COUNT(f) > 0 FROM HelloAssoDonationFunds f
        WHERE f.fundsState NOT IN (org.commonlink.entity.FundsState.AVAILABLE, org.commonlink.entity.FundsState.WITHDRAWN)
    """)
    fun existsTrackable(): Boolean

    /** The donations of one association paid out in HelloAsso payout [idCashOut] (transfer receipt confirmation). */
    @Query("""
        SELECT f FROM HelloAssoDonationFunds f
        JOIN FETCH f.donation d
        WHERE d.campaign.association.id = :associationId AND f.idCashOut = :idCashOut
        ORDER BY f.donationId
    """)
    fun findByAssociationIdAndIdCashOut(
        @Param("associationId") associationId: UUID,
        @Param("idCashOut") idCashOut: Long,
    ): List<HelloAssoDonationFunds>

    /**
     * Loads a row holding a write lock until the end of the transaction, so that two writers (tracking pass,
     * refund notification, receipt confirmation) never overwrite each other's transition. Native `FOR UPDATE`, which
     * PostgreSQL and the test suite's H2 both accept.
     */
    @Query(value = "SELECT * FROM helloasso_donation_funds WHERE donation_id = :donationId FOR UPDATE", nativeQuery = true)
    fun findByDonationIdForUpdate(@Param("donationId") donationId: UUID): HelloAssoDonationFunds?
}

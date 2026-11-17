package org.commonlink.repository

import org.commonlink.entity.HelloAssoCashOut
import org.commonlink.entity.HelloAssoCashOutKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Net totals of HelloAsso payouts, keyed by (association, payout id) — the primary key backs every lookup. */
interface HelloAssoCashOutRepository : JpaRepository<HelloAssoCashOut, HelloAssoCashOutKey> {

    /** The stored total of payout [idCashOut] of [associationId], if already read. */
    fun findByAssociationIdAndIdCashOut(associationId: UUID, idCashOut: Long): HelloAssoCashOut?

    /**
     * Stores a total unless one is already there (two concurrent first reads of the same payout): never raises a
     * duplicate-key error. Returns the number of rows inserted (0 or 1).
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO helloasso_cash_outs (association_id, id_cash_out, net_total, fetched_at)
            VALUES (:associationId, :idCashOut, :netTotal, :fetchedAt)
            ON CONFLICT DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfMissing(
        @Param("associationId") associationId: UUID,
        @Param("idCashOut") idCashOut: Long,
        @Param("netTotal") netTotal: BigDecimal,
        @Param("fetchedAt") fetchedAt: Instant,
    ): Int
}

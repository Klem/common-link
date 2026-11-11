package org.commonlink.entity

import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Net amount of one HelloAsso payout of an association, as it reaches its bank account (Σ credits − Σ debits of
 * HelloAsso's payout export, in euros). Only this total is kept: the export's rows carry third-party payers' data and
 * are never stored. Written once, the first time the payout's total is needed.
 */
@Entity
@Table(name = "helloasso_cash_outs")
@IdClass(HelloAssoCashOutKey::class)
class HelloAssoCashOut(

    @Id
    @Column(name = "association_id", nullable = false, updatable = false)
    val associationId: UUID,

    /** HelloAsso's payout id (`idCashOut` on a payment, `cashOutId` in the export). */
    @Id
    @Column(name = "id_cash_out", nullable = false, updatable = false)
    val idCashOut: Long,

    /** Net amount in euros; may include other payments than CommonLink's and be lowered by refunds. */
    @Column(name = "net_total", nullable = false, precision = 12, scale = 2)
    val netTotal: BigDecimal,

    @Column(name = "fetched_at", nullable = false, updatable = false)
    val fetchedAt: Instant = Instant.now(),
)

/** Composite key of [HelloAssoCashOut]. */
data class HelloAssoCashOutKey(val associationId: UUID? = null, val idCashOut: Long? = null) : Serializable

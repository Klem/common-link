package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * An outgoing payment (payout) from a campaign to a payee.
 *
 * Created as [PayoutStatus.PENDING], then confirmed by the association which transitions it to
 * [PayoutStatus.CONFIRMED] and enqueues a [OnchainJobAction.RECORD_PAYOUT] on-chain job.
 * On terminal failure the status becomes [PayoutStatus.FAILED].
 *
 * The [typeCode] stores the French plan comptable category code (e.g. "60-mat", "64-rem")
 * selected by the user; [kind] is the derived high-level group used for budget reporting.
 */
@Entity
@Table(name = "payouts")
class Payout(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    val campaign: Campaign,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_id", nullable = false)
    val payee: Payee,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_iban_id", nullable = false)
    val payeeIban: PayeeIban,

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    val kind: PayoutKind,

    /** French plan comptable code, e.g. "60-mat", "64-rem". */
    @Column(name = "type_code", nullable = false, length = 50)
    val typeCode: String,

    /** Justification text — object of payment, invoice reference, etc. */
    @Column(name = "label", nullable = false, length = 500)
    val label: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PayoutStatus = PayoutStatus.PENDING,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,

    /** UUID of the [OnchainJob] enqueued on confirmation; null until confirmed. */
    @Column(name = "onchain_job_id")
    var onchainJobId: UUID? = null,
)

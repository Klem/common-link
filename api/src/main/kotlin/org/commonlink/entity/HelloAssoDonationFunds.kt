package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Cash-out tracking of one confirmed HelloAsso donation: where its money stands between HelloAsso and the
 * association's bank account ([fundsState]). Created at the donation's confirmation, one-to-one with [donation].
 *
 * Holds HelloAsso ids and states only, never payer data. [cashOutState] is HelloAsso's raw value (diagnosis); the
 * transitions are computed by [org.commonlink.service.HelloAssoFundsStateMapper].
 */
@Entity
@Table(name = "helloasso_donation_funds")
class HelloAssoDonationFunds(

    /** The tracked donation (`helloasso:` provider reference). */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donation_id", nullable = false)
    val donation: Donation,

    /** Id of the HelloAsso payment that confirmed the donation, read back by `GET /v5/payments/{id}`. */
    @Column(name = "ha_payment_id", nullable = false, updatable = false)
    val haPaymentId: Long,

    /** Last payout state read at HelloAsso, raw (`Transfered`, `CashedOut`…). */
    @Column(name = "cash_out_state", length = 64)
    var cashOutState: String? = null,

    /** Our funds state; see [FundsState]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "funds_state", nullable = false, length = 20)
    var fundsState: FundsState = FundsState.AT_PROVIDER,

    /** HelloAsso's payout date, once paid out: start of the settlement delay. */
    @Column(name = "cash_out_date")
    var cashOutDate: Instant? = null,

    /** HelloAsso payout grouping this payment with others, once paid out. */
    @Column(name = "id_cash_out")
    var idCashOut: Long? = null,

    /** When `CashedOut` was first observed: start of the settlement delay when [cashOutDate] is missing. */
    @Column(name = "cashed_out_seen_at")
    var cashedOutSeenAt: Instant? = null,

    /** Last successful read at HelloAsso. */
    @Column(name = "last_checked_at")
    var lastCheckedAt: Instant? = null,

    /** First observation of an `Unknown` or unrecognised payout state; null once a known one is read. */
    @Column(name = "unknown_since")
    var unknownSince: Instant? = null,

    /** When the persistent unknown state was alerted (once per occurrence). */
    @Column(name = "unknown_alerted_at")
    var unknownAlertedAt: Instant? = null,

    /** When the "paid out but not arrived for too long" alert was raised (once). */
    @Column(name = "in_transit_alerted_at")
    var inTransitAlertedAt: Instant? = null,

    /** When the donation became [FundsState.AVAILABLE]. */
    @Column(name = "receipt_confirmed_at")
    var receiptConfirmedAt: Instant? = null,

    /** How it became available; see [ReceiptConfirmationMode]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_confirmation_mode", length = 20)
    var receiptConfirmationMode: ReceiptConfirmationMode? = null,

    /** The user who confirmed the receipt or released the funds; null for [ReceiptConfirmationMode.AUTO]. */
    @Column(name = "receipt_confirmed_by")
    var receiptConfirmedBy: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    /** Same value as the donation's id (shared primary key). */
    @Id
    @Column(name = "donation_id", nullable = false, updatable = false)
    var donationId: UUID? = null
}

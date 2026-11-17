package org.commonlink.service

import org.commonlink.entity.FundsState
import java.time.Instant

/**
 * Maps what HelloAsso says about a payment (its `state` and payout `cashOutState`) to the [FundsState] of the donation
 * it confirmed. Pure function, no dependency: every uncertain HelloAsso behaviour is decided here
 * (`.tasks/handoff-mise-a-disposition-fonds-helloasso.md` §3.5 and §4.1).
 *
 * | `cashOutState`                                              | target                    |
 * |-------------------------------------------------------------|---------------------------|
 * | `MoneyIn`, `TransferInProgress`, `Transfered`               | [FundsState.AT_PROVIDER]  |
 * | `CantTransferReceiverFull`                                  | AT_PROVIDER + [Signal.RECEIVER_FULL] |
 * | `WaitingForCashOutConfirmation`                             | [FundsState.PAYOUT_PREPARING] |
 * | `CashedOut`                                                 | [FundsState.IN_TRANSIT], payout date and id set |
 * | `Refunding`, `Refunded`, `Contested` (or `state` reversed)  | [FundsState.WITHDRAWN]    |
 * | `Unknown`, absent, or any other value                       | no transition + [Signal.UNKNOWN_STATE] / [Signal.UNRECOGNISED_STATE] |
 *
 * Rules:
 * - never backwards: a state earlier than the current one is ignored ([Signal.REGRESSION_IGNORED]), except
 *   [FundsState.WITHDRAWN], reachable from any state, [FundsState.AVAILABLE] included;
 * - [FundsState.AVAILABLE] and [FundsState.WITHDRAWN] are terminal: only a refund leaves AVAILABLE;
 * - `IN_TRANSIT → AVAILABLE` is not decided here (settlement delay or transfer receipt, see
 *   [HelloAssoCashOutSyncExecutor]);
 * - the payment `state` matters only when reversed ([REVERSED_STATES]): `Registered` is a paid state like
 *   `Authorized`, never a withdrawal.
 */
object HelloAssoFundsStateMapper {

    /** Payment states meaning the money went back to the payer (same set as [HelloAssoCheckoutService]). */
    val REVERSED_STATES = setOf("Refunded", "Refunding", "Contested")

    /** The ten `cashOutState` values HelloAsso documents in its SDK. */
    val KNOWN_CASH_OUT_STATES = setOf(
        "MoneyIn", "TransferInProgress", "Transfered", "CantTransferReceiverFull", "Unknown",
        "WaitingForCashOutConfirmation", "CashedOut", "Refunding", "Refunded", "Contested",
    )

    private val AT_PROVIDER_STATES = setOf("MoneyIn", "TransferInProgress", "Transfered", "CantTransferReceiverFull")
    private val WITHDRAWN_CASH_OUT_STATES = setOf("Refunding", "Refunded", "Contested")

    /** Something worth a log or an alert, whatever the transition. */
    enum class Signal {
        /** `CantTransferReceiverFull`: HelloAsso cannot move the money. Technical alert. */
        RECEIVER_FULL,
        /** `Unknown` or no `cashOutState`: no transition, logged; alerted when it persists. */
        UNKNOWN_STATE,
        /** A value outside [KNOWN_CASH_OUT_STATES]: handled like `Unknown`, logged as unexpected. */
        UNRECOGNISED_STATE,
        /** A state earlier than the current one: ignored, logged. */
        REGRESSION_IGNORED,
    }

    /**
     * What HelloAsso reported for the payment, at [observedAt].
     *
     * @property state The payment state (`Authorized`, `Registered`, `Refunded`…).
     * @property cashOutState The raw payout state.
     * @property cashOutDate HelloAsso's payout date, if any.
     * @property idCashOut HelloAsso's payout id, if any.
     */
    data class Observation(
        val state: String?,
        val cashOutState: String?,
        val cashOutDate: Instant?,
        val idCashOut: Long?,
        val observedAt: Instant,
    )

    /**
     * Result of [map]. Non-null fields are to be written; null ones left as they are (never cleared).
     *
     * @property target The new state, or null when the state does not change.
     * @property cashOutDate Payout date to record (on `CashedOut`).
     * @property idCashOut Payout id to record (on `CashedOut`).
     * @property cashedOutSeenAt First observation of `CashedOut` (only on the transition into IN_TRANSIT): start of the
     *   settlement delay when [cashOutDate] is missing.
     */
    data class Outcome(
        val target: FundsState?,
        val cashOutDate: Instant? = null,
        val idCashOut: Long? = null,
        val cashedOutSeenAt: Instant? = null,
        val signals: Set<Signal> = emptySet(),
    )

    /** Maps [observation] against [current] (null: the row does not exist yet, at the donation's confirmation). */
    fun map(current: FundsState?, observation: Observation): Outcome {
        val cashOutState = observation.cashOutState
        if (cashOutState in WITHDRAWN_CASH_OUT_STATES || observation.state in REVERSED_STATES) {
            return Outcome(if (current == FundsState.WITHDRAWN) null else FundsState.WITHDRAWN)
        }
        if (current == FundsState.AVAILABLE || current == FundsState.WITHDRAWN) return Outcome(null)

        val signals = mutableSetOf<Signal>()
        val reported = when (cashOutState) {
            in AT_PROVIDER_STATES -> FundsState.AT_PROVIDER
            "WaitingForCashOutConfirmation" -> FundsState.PAYOUT_PREPARING
            "CashedOut" -> FundsState.IN_TRANSIT
            null, "Unknown" -> {
                signals += Signal.UNKNOWN_STATE
                null
            }
            else -> {
                signals += Signal.UNRECOGNISED_STATE
                null
            }
        }
        if (cashOutState == "CantTransferReceiverFull") signals += Signal.RECEIVER_FULL
        if (reported == null) return Outcome(null, signals = signals)

        val target = when {
            rank(reported) > rank(current) -> reported
            rank(reported) < rank(current) -> {
                signals += Signal.REGRESSION_IGNORED
                null
            }
            else -> null
        }
        if (reported != FundsState.IN_TRANSIT) return Outcome(target, signals = signals)
        return Outcome(
            target = target,
            cashOutDate = observation.cashOutDate,
            idCashOut = observation.idCashOut,
            cashedOutSeenAt = if (target == FundsState.IN_TRANSIT) observation.observedAt else null,
            signals = signals,
        )
    }

    private fun rank(state: FundsState?): Int = when (state) {
        null -> -1
        FundsState.AT_PROVIDER -> 0
        FundsState.PAYOUT_PREPARING -> 1
        FundsState.IN_TRANSIT -> 2
        FundsState.AVAILABLE, FundsState.WITHDRAWN -> 3
    }
}

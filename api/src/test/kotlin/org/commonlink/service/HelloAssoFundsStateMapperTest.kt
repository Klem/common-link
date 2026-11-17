package org.commonlink.service

import org.commonlink.entity.FundsState
import org.commonlink.entity.FundsState.AT_PROVIDER
import org.commonlink.entity.FundsState.AVAILABLE
import org.commonlink.entity.FundsState.IN_TRANSIT
import org.commonlink.entity.FundsState.PAYOUT_PREPARING
import org.commonlink.entity.FundsState.WITHDRAWN
import org.commonlink.service.HelloAssoFundsStateMapper.Observation
import org.commonlink.service.HelloAssoFundsStateMapper.Signal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Every `cashOutState` value against every current state (null = row not created yet): 10 × 6 combinations, each
 * with its expected target and signals written by hand, plus reversed payment states and values outside the ten.
 */
class HelloAssoFundsStateMapperTest {

    private val observedAt = Instant.parse("2026-11-12T08:00:00Z")
    private val payoutDate = Instant.parse("2026-11-12T09:00:00Z")
    private val currents: List<FundsState?> = listOf(null, AT_PROVIDER, PAYOUT_PREPARING, IN_TRANSIT, AVAILABLE, WITHDRAWN)

    private fun observe(cashOutState: String?, state: String = "Authorized", date: Instant? = null, idCashOut: Long? = null) =
        Observation(state, cashOutState, date, idCashOut, observedAt)

    private val R = Signal.REGRESSION_IGNORED

    /** Expected (target, signals) per current state, in the order of [currents]. */
    private val table: Map<String, List<Pair<FundsState?, Set<Signal>>>> = mapOf(
        "MoneyIn" to listOf(AT_PROVIDER to emptySet(), null to emptySet(), null to setOf(R), null to setOf(R), null to emptySet(), null to emptySet()),
        "TransferInProgress" to listOf(AT_PROVIDER to emptySet(), null to emptySet(), null to setOf(R), null to setOf(R), null to emptySet(), null to emptySet()),
        "Transfered" to listOf(AT_PROVIDER to emptySet(), null to emptySet(), null to setOf(R), null to setOf(R), null to emptySet(), null to emptySet()),
        "CantTransferReceiverFull" to listOf(
            AT_PROVIDER to setOf(Signal.RECEIVER_FULL), null to setOf(Signal.RECEIVER_FULL), null to setOf(Signal.RECEIVER_FULL, R),
            null to setOf(Signal.RECEIVER_FULL, R), null to emptySet(), null to emptySet(),
        ),
        "Unknown" to listOf(
            null to setOf(Signal.UNKNOWN_STATE), null to setOf(Signal.UNKNOWN_STATE), null to setOf(Signal.UNKNOWN_STATE),
            null to setOf(Signal.UNKNOWN_STATE), null to emptySet(), null to emptySet(),
        ),
        "WaitingForCashOutConfirmation" to listOf(
            PAYOUT_PREPARING to emptySet(), PAYOUT_PREPARING to emptySet(), null to emptySet(), null to setOf(R), null to emptySet(), null to emptySet(),
        ),
        "CashedOut" to listOf(
            IN_TRANSIT to emptySet(), IN_TRANSIT to emptySet(), IN_TRANSIT to emptySet(), null to emptySet(), null to emptySet(), null to emptySet(),
        ),
        "Refunding" to listOf(WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), null to emptySet()),
        "Refunded" to listOf(WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), null to emptySet()),
        "Contested" to listOf(WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), WITHDRAWN to emptySet(), null to emptySet()),
    )

    @Test
    fun `the table covers the ten documented values`() {
        assertEquals(HelloAssoFundsStateMapper.KNOWN_CASH_OUT_STATES, table.keys)
    }

    @Test
    fun `every cashOutState against every current state`() {
        for ((cashOutState, expected) in table) {
            currents.zip(expected).forEach { (current, pair) ->
                val outcome = HelloAssoFundsStateMapper.map(current, observe(cashOutState))
                assertEquals(pair.first, outcome.target, "$cashOutState from $current: target")
                assertEquals(pair.second, outcome.signals, "$cashOutState from $current: signals")
            }
        }
    }

    @Test
    fun `a reversed payment state withdraws from any state but WITHDRAWN, whatever the cashOutState`() {
        for (state in listOf("Refunded", "Refunding", "Contested")) {
            currents.forEach { current ->
                val outcome = HelloAssoFundsStateMapper.map(current, observe("CashedOut", state = state))
                assertEquals(if (current == WITHDRAWN) null else WITHDRAWN, outcome.target, "$state from $current")
            }
        }
    }

    @Test
    fun `Registered is a paid state, never a withdrawal`() {
        assertEquals(AT_PROVIDER, HelloAssoFundsStateMapper.map(null, observe("Transfered", state = "Registered")).target)
        assertNull(HelloAssoFundsStateMapper.map(AVAILABLE, observe("CashedOut", state = "Registered")).target)
    }

    @Test
    fun `a value outside the ten, or no value, is handled like Unknown`() {
        currents.filter { it != AVAILABLE && it != WITHDRAWN }.forEach { current ->
            val unrecognised = HelloAssoFundsStateMapper.map(current, observe("SomethingNew"))
            assertNull(unrecognised.target)
            assertEquals(setOf(Signal.UNRECOGNISED_STATE), unrecognised.signals)

            val absent = HelloAssoFundsStateMapper.map(current, observe(null))
            assertNull(absent.target)
            assertEquals(setOf(Signal.UNKNOWN_STATE), absent.signals)
        }
    }

    @Test
    fun `CashedOut records the payout date and id, and the first observation`() {
        val outcome = HelloAssoFundsStateMapper.map(AT_PROVIDER, observe("CashedOut", date = payoutDate, idCashOut = 60001))

        assertEquals(IN_TRANSIT, outcome.target)
        assertEquals(payoutDate, outcome.cashOutDate)
        assertEquals(60001L, outcome.idCashOut)
        assertEquals(observedAt, outcome.cashedOutSeenAt)
    }

    @Test
    fun `CashedOut without a payout date still starts the delay at the observation`() {
        val outcome = HelloAssoFundsStateMapper.map(PAYOUT_PREPARING, observe("CashedOut"))

        assertEquals(IN_TRANSIT, outcome.target)
        assertNull(outcome.cashOutDate)
        assertEquals(observedAt, outcome.cashedOutSeenAt)
    }

    @Test
    fun `CashedOut read again while in transit completes a missing date without moving the delay start`() {
        val outcome = HelloAssoFundsStateMapper.map(IN_TRANSIT, observe("CashedOut", date = payoutDate, idCashOut = 60001))

        assertNull(outcome.target)
        assertEquals(payoutDate, outcome.cashOutDate)
        assertEquals(60001L, outcome.idCashOut)
        assertNull(outcome.cashedOutSeenAt)
    }

    @Test
    fun `IN_TRANSIT never becomes AVAILABLE here`() {
        table.keys.forEach { assertEquals(false, HelloAssoFundsStateMapper.map(IN_TRANSIT, observe(it)).target == AVAILABLE, it) }
    }
}

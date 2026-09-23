package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.BridgeProperties
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.PayoutStatus
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Covers the backstop the webhook path does not have: a notification Bridge considers delivered is
 * never sent again, so a payout it failed to move stays where it is for ever unless something
 * re-reads it.
 */
class BridgePayoutReconcilerTest {

    private val payouts = mockk<PayoutRepository>()
    private val webhookService = mockk<BridgeWebhookService>(relaxed = true)
    private val alerts = mockk<TechnicalAlertService>(relaxed = true)

    private fun reconciler(props: BridgeProperties = realProps()) =
        BridgePayoutReconciler(payouts, webhookService, alerts, props)

    private fun realProps(
        enabled: Boolean = true,
        demoMode: Boolean = false,
        stuckAfter: Duration = Duration.ofDays(3),
    ) = BridgeProperties(
        demoMode = demoMode,
        clientId = "cid",
        clientSecret = "csecret",
        reconciler = BridgeProperties.Reconciler(enabled = enabled, stuckAfter = stuckAfter),
    )

    private fun routing(
        id: UUID = UUID.randomUUID(),
        linkId: String? = "pl_1",
        bridgeStatus: BridgePaymentStatus? = BridgePaymentStatus.PDNG,
        syncedAt: Instant? = Instant.now().minus(Duration.ofDays(10)),
    ) = object : PayoutRepository.PayoutRouting {
        override val id = id
        override val bridgePaymentLinkId = linkId
        override val status = PayoutStatus.PENDING
        override val bridgeStatus = bridgeStatus
        override val bridgeSyncedAt = syncedAt
    }

    @Test
    fun `replays each stale payout through the one handler that knows the transitions`() {
        // Not a second implementation of settle/fail/release: the sweep re-reads and hands over, so
        // there is exactly one place where Bridge's view becomes a payout's state.
        val first = routing(linkId = "pl_1")
        val second = routing(linkId = "pl_2")
        every { payouts.findStaleInFlight(any()) } returns listOf(first, second)
        every { payouts.findRoutingById(any()) } returns routing(bridgeStatus = BridgePaymentStatus.ACSC)

        reconciler().sweep()

        verify { webhookService.handlePaymentLinkNotification("pl_1", first.id.toString()) }
        verify { webhookService.handlePaymentLinkNotification("pl_2", second.id.toString()) }
    }

    @Test
    fun `one payout failing never stops the sweep`() {
        // An unreachable Bridge would otherwise leave every later row unexamined until the next tick.
        val failing = routing(linkId = "pl_bad")
        val healthy = routing(linkId = "pl_ok")
        every { payouts.findStaleInFlight(any()) } returns listOf(failing, healthy)
        every { payouts.findRoutingById(any()) } returns routing(bridgeStatus = BridgePaymentStatus.ACSC)
        every {
            webhookService.handlePaymentLinkNotification("pl_bad", any(), any())
        } throws RuntimeException("Bridge unreachable")

        reconciler().sweep()

        verify { webhookService.handlePaymentLinkNotification("pl_ok", healthy.id.toString()) }
    }

    @Test
    fun `escalates a payout still past authorisation long after the bank should have executed`() {
        // Bridge documents that some banks (LCL, Nickel) never report an execution status: their
        // flow stops at PDNG. Nothing here promotes it — the attestation is irretractable — so a
        // human is asked to confirm from the statement.
        val stuck = routing(syncedAt = Instant.now().minus(Duration.ofDays(10)))
        every { payouts.findStaleInFlight(any()) } returns listOf(stuck)
        every { payouts.findRoutingById(stuck.id) } returns routing(bridgeStatus = BridgePaymentStatus.PDNG)

        reconciler().sweep()

        verify { alerts.reportFailure(TechnicalAlertKind.PAYOUT_STUCK_IN_FLIGHT, any(), any(), any()) }
        verify(exactly = 0) { payouts.save(any()) }
    }

    @Test
    fun `does not escalate a payout merely waiting on an association`() {
        // CREA and ACTC mean nobody has authorised yet, which the link's expiry resolves on its
        // own. Escalating that would report ordinary hesitation as an incident.
        val waiting = routing(syncedAt = Instant.now().minus(Duration.ofDays(10)))
        every { payouts.findStaleInFlight(any()) } returns listOf(waiting)
        every { payouts.findRoutingById(waiting.id) } returns routing(bridgeStatus = BridgePaymentStatus.ACTC)

        reconciler().sweep()

        verify(exactly = 0) { alerts.reportFailure(TechnicalAlertKind.PAYOUT_STUCK_IN_FLIGHT, any(), any(), any()) }
    }

    @Test
    fun `does not escalate a payout that has simply not waited long enough`() {
        val recent = routing(syncedAt = Instant.now().minus(Duration.ofHours(8)))
        every { payouts.findStaleInFlight(any()) } returns listOf(recent)
        every { payouts.findRoutingById(recent.id) } returns routing(bridgeStatus = BridgePaymentStatus.PDNG)

        reconciler().sweep()

        verify(exactly = 0) { alerts.reportFailure(TechnicalAlertKind.PAYOUT_STUCK_IN_FLIGHT, any(), any(), any()) }
    }

    @Test
    fun `never sweeps in demo mode`() {
        // getPaymentLink answers ACSC unconditionally there: a sweep would settle every pending
        // payout on a simulation, and publish an attestation for each.
        reconciler(realProps(demoMode = true)).sweep()

        verify(exactly = 0) { payouts.findStaleInFlight(any()) }
        verify(exactly = 0) { webhookService.handlePaymentLinkNotification(any(), any(), any()) }
    }

    @Test
    fun `does nothing when disabled`() {
        reconciler(realProps(enabled = false)).sweep()

        verify(exactly = 0) { payouts.findStaleInFlight(any()) }
    }
}

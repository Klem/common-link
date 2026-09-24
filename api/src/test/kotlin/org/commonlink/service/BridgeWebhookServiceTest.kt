package org.commonlink.service

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.commonlink.config.BridgeProperties
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Payee
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.BadGatewayException
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Covers the webhook path, which is the *only* driver of payout settlement — there is no polling
 * loop, so anything this class gets wrong strands a payout or, worse, publishes an on-chain
 * attestation for a transfer that never settled.
 */
class BridgeWebhookServiceTest {

    private val payoutRepository = mockk<PayoutRepository>()
    private val bridgeInitiation = mockk<BridgePaymentInitiationService>()
    private val confirmer = mockk<PayoutConfirmer>(relaxed = true)
    private val alerts = mockk<TechnicalAlertService>(relaxed = true)

    /** Grace window for the deferred release; long enough that "now" is always inside it. */
    private val props = BridgeProperties(releaseGrace = Duration.ofMinutes(3))

    private val service = BridgeWebhookService(payoutRepository, bridgeInitiation, confirmer, alerts, props)

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val assoc = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789")
    private val campaign = Campaign(association = assoc, name = "Camp", emoji = "🌍", description = "desc",
        goal = BigDecimal("10000"), status = CampaignStatus.LIVE)
    private val payee = Payee(association = assoc, name = "Payee", identifier1 = "123456789")

    private val payout = Payout(
        campaign = campaign, payee = payee, payeeIbanId = UUID.randomUUID(),
        payeeIbanValue = "FR7630006000011234567890189", amount = BigDecimal("500"),
        kind = PayoutKind.EXPENSE, typeCode = "60-mat", label = "Achat materiel pedagogique",
        bridgePaymentLinkId = LINK_ID, bridgeStatus = BridgePaymentStatus.CREA,
    )

    /**
     * What routing is allowed to know. Deliberately not the [Payout] entity: loading it here is
     * what let the confirmer's locked read be answered from the persistence context instead of the
     * database, so this test pins the projection rather than merely accepting it.
     */
    private fun routing(
        payoutId: UUID = payout.id,
        linkId: String? = LINK_ID,
        payoutStatus: PayoutStatus = PayoutStatus.PENDING,
        engaged: BridgePaymentStatus? = BridgePaymentStatus.CREA,
        syncedAt: Instant? = Instant.now(),
    ) = object : PayoutRepository.PayoutRouting {
        override val id = payoutId
        override val bridgePaymentLinkId = linkId
        override val status = payoutStatus
        override val bridgeStatus = engaged
        override val bridgeSyncedAt = syncedAt
    }

    /**
     * @param syncedAt When the payout's Bridge state last moved. Defaults to well outside the
     *   release grace: the ordinary case is a link that outlives the last thing that happened on
     *   it, and only the deferral tests care about the recent one.
     */
    private fun stubState(
        status: BridgePaymentStatus,
        transactionId: String? = "tx_1",
        statusReason: String? = null,
        syncedAt: Instant = Instant.now().minus(Duration.ofHours(1)),
    ) {
        every { payoutRepository.findRoutingByBridgePaymentLinkId(LINK_ID) } returns routing(syncedAt = syncedAt)
        every { bridgeInitiation.getPaymentLink(LINK_ID) } returns
            BridgePaymentLinkState(status, transactionId, statusReason)
    }

    @Test
    fun `settles the payout when Bridge reports ACSC`() {
        stubState(BridgePaymentStatus.ACSC)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.finaliseSettled(payout.id, "tx_1") }
    }

    @Test
    fun `alerts when a settlement lands on an amount already given back`() {
        // The amount is reserved only while the payout is PENDING with a Bridge status on it.
        // Settling a released one is recorded anyway — the bank executed the transfer — but the
        // association may have committed those funds elsewhere in between, and nothing re-checks
        // the balance at settlement because there is nothing left to refuse.
        every { payoutRepository.findRoutingByBridgePaymentLinkId(LINK_ID) } returns
            routing(payoutStatus = PayoutStatus.FAILED, engaged = BridgePaymentStatus.RJCT)
        every { bridgeInitiation.getPaymentLink(LINK_ID) } returns
            BridgePaymentLinkState(BridgePaymentStatus.ACSC, "tx_1", null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { alerts.reportFailure(TechnicalAlertKind.PAYOUT_SETTLED_AFTER_RELEASE, any(), any(), any()) }
        // Recorded regardless: a ledger refusing to record a movement it observes lies more gravely.
        verify { confirmer.finaliseSettled(payout.id, "tx_1") }
    }

    @Test
    fun `stays silent when the settled amount was still engaged`() {
        stubState(BridgePaymentStatus.ACSC)

        service.handlePaymentLinkNotification(LINK_ID)

        verify(exactly = 0) { alerts.reportFailure(TechnicalAlertKind.PAYOUT_SETTLED_AFTER_RELEASE, any(), any(), any()) }
    }

    @Test
    fun `stays silent when a settlement is redelivered on a payout already confirmed`() {
        // Observed 2026-09-24 08:58:45: a notification for a superseded link resolved through the
        // client_reference fallback, re-read the current link, found ACSC — and the payout had
        // been correctly CONFIRMED forty minutes earlier. Its amount was never given back, so
        // there is nothing for a human to reconcile. Bridge redelivers for up to two days; an
        // alert on every replay is an alert nobody reads the day it means something.
        every { payoutRepository.findRoutingByBridgePaymentLinkId(LINK_ID) } returns
            routing(payoutStatus = PayoutStatus.CONFIRMED, engaged = BridgePaymentStatus.ACSC)
        every { bridgeInitiation.getPaymentLink(LINK_ID) } returns
            BridgePaymentLinkState(BridgePaymentStatus.ACSC, "tx_1", null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify(exactly = 0) { alerts.reportFailure(TechnicalAlertKind.PAYOUT_SETTLED_AFTER_RELEASE, any(), any(), any()) }
        // Still routed to the confirmer, whose own idempotence guard makes it a no-op.
        verify { confirmer.finaliseSettled(payout.id, "tx_1") }
    }

    @Test
    fun `alerts when a settlement lands on a payout released back to PENDING`() {
        // The other half of "the amount was given back": an expiry cleared bridgeStatus and
        // returned the amount to the campaign, and the transfer settled anyway.
        every { payoutRepository.findRoutingByBridgePaymentLinkId(LINK_ID) } returns
            routing(payoutStatus = PayoutStatus.PENDING, engaged = null)
        every { bridgeInitiation.getPaymentLink(LINK_ID) } returns
            BridgePaymentLinkState(BridgePaymentStatus.ACSC, "tx_1", null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { alerts.reportFailure(TechnicalAlertKind.PAYOUT_SETTLED_AFTER_RELEASE, any(), any(), any()) }
        verify { confirmer.finaliseSettled(payout.id, "tx_1") }
    }

    @Test
    fun `never trusts the notification body — the state is always re-read from Bridge`() {
        // Bridge publishes no webhook signature, so acting on the payload would let anyone who can
        // reach the endpoint publish an irretractable on-chain attestation.
        stubState(BridgePaymentStatus.CREA)

        service.handlePaymentLinkNotification(LINK_ID)

        verify(exactly = 1) { bridgeInitiation.getPaymentLink(LINK_ID) }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
    }

    @Test
    fun `fails the payout with the bank's reason when Bridge reports RJCT`() {
        stubState(BridgePaymentStatus.RJCT, statusReason = "debit_account_insufficient_funds")
        every { bridgeInitiation.revokePaymentLink(LINK_ID) } just Runs

        service.handlePaymentLinkNotification(LINK_ID)

        verify {
            confirmer.finaliseFailed(
                payout.id, "debit_account_insufficient_funds", BridgePaymentStatus.RJCT,
            )
        }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
    }

    @Test
    fun `revokes the link before releasing the amount on a rejection`() {
        // Bridge leaves a rejected link usable — observed 2026-09-22, a link whose request came
        // back RJCT still read `Valide` and a second authorisation on it settled. Failing first
        // would hand the amount back to the campaign while that URL is still live.
        stubState(BridgePaymentStatus.RJCT)
        every { bridgeInitiation.revokePaymentLink(LINK_ID) } just Runs

        service.handlePaymentLinkNotification(LINK_ID)

        verifyOrder {
            bridgeInitiation.revokePaymentLink(LINK_ID)
            confirmer.finaliseFailed(payout.id, any(), BridgePaymentStatus.RJCT)
        }
    }

    @Test
    fun `leaves the amount engaged when the link could not be revoked`() {
        // Releasing it would be releasing against a link that may still be authorisable. The
        // notification is answered non-2xx instead, and Bridge redelivers it.
        stubState(BridgePaymentStatus.RJCT)
        every { bridgeInitiation.revokePaymentLink(LINK_ID) } throws BadGatewayException("unreachable")

        assertThrows<BadGatewayException> { service.handlePaymentLinkNotification(LINK_ID) }

        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    @Test
    fun `does not revoke again on a redelivered rejection`() {
        // Bridge sends several notifications per state change and retries for up to two days; the
        // link was revoked on the first one.
        every { payoutRepository.findRoutingByBridgePaymentLinkId(LINK_ID) } returns
            routing(payoutStatus = PayoutStatus.FAILED)
        every { bridgeInitiation.getPaymentLink(LINK_ID) } returns
            BridgePaymentLinkState(BridgePaymentStatus.RJCT, "tx_1", null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify(exactly = 0) { bridgeInitiation.revokePaymentLink(any()) }
        verify { confirmer.finaliseFailed(payout.id, any(), BridgePaymentStatus.RJCT) }
    }

    @Test
    fun `does not revoke on a late rejection for a payout already settled`() {
        // A link can carry a settled request and a rejected sibling, so a late RJCT is reachable
        // on a CONFIRMED payout. finaliseFailed refuses to un-settle it, so the revocation would
        // protect nothing — while a 5xx on POST /revoke throws, answers the webhook 502 and has
        // Bridge redeliver for two days over a call whose only outcome was a no-op.
        every { payoutRepository.findRoutingByBridgePaymentLinkId(LINK_ID) } returns
            routing(payoutStatus = PayoutStatus.CONFIRMED, engaged = BridgePaymentStatus.ACSC)
        every { bridgeInitiation.getPaymentLink(LINK_ID) } returns
            BridgePaymentLinkState(BridgePaymentStatus.RJCT, "tx_1", null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify(exactly = 0) { bridgeInitiation.revokePaymentLink(any()) }
        // Still routed to the confirmer, which is where the refusal to un-settle lives.
        verify { confirmer.finaliseFailed(payout.id, any(), BridgePaymentStatus.RJCT) }
    }

    @Test
    fun `returns the payout to a retryable state when the authorisation window expired`() {
        // An expiry is nobody ever asking, not the bank refusing. Nothing was debited, so failing
        // the payout would retire it for good because the association closed the tab.
        stubState(BridgePaymentStatus.LINK_EXPIRED, transactionId = null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.releaseReservation(payout.id, any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    @Test
    fun `returns the payout to a retryable state when the link was revoked unused`() {
        stubState(BridgePaymentStatus.LINK_REVOKED, transactionId = null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.releaseReservation(payout.id, any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    @Test
    fun `leaves the amount engaged when the link died while the association was at its bank`() {
        // Bridge stamps ACTC the instant the payer enters the tunnel and keeps it for the whole
        // bank authentication — CREA 08:18:53, ACTC 08:18:55, terminal answer 08:20:16 on
        // 2026-09-24. Someone who starts authorising a minute before expired_date is therefore
        // still ACTC when the link dies, and releasing there hands the amount back to the campaign
        // while the bank goes on to execute the transfer.
        stubState(BridgePaymentStatus.LINK_EXPIRED, transactionId = null, syncedAt = Instant.now())

        service.handlePaymentLinkNotification(LINK_ID)

        verify(exactly = 0) { confirmer.releaseReservation(any(), any()) }
        // And nothing is written: touching bridgeSyncedAt would reset the clock this reads and
        // defer the release for ever. The payout keeps ageing until Bridge redelivers or the
        // reconciler picks it up.
        verify(exactly = 0) { confirmer.recordInFlight(any(), any(), any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    @Test
    fun `releases once the grace window has passed`() {
        // The deferral is a pause, not a veto: the same notification redelivered later, or the
        // reconciler's replay, must reach the release.
        stubState(
            BridgePaymentStatus.LINK_EXPIRED,
            transactionId = null,
            syncedAt = Instant.now().minus(Duration.ofMinutes(4)),
        )

        service.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.releaseReservation(payout.id, any()) }
    }

    @Test
    fun `defers a revocation the same way`() {
        // Same hazard, same reasoning: a link revoked from the Bridge dashboard mid-authentication
        // is a dead link over a request that may be seconds from PDNG.
        stubState(BridgePaymentStatus.LINK_REVOKED, transactionId = null, syncedAt = Instant.now())

        service.handlePaymentLinkNotification(LINK_ID)

        verify(exactly = 0) { confirmer.releaseReservation(any(), any()) }
    }

    @Test
    fun `releases without waiting when the grace is disabled`() {
        val noGrace = BridgeWebhookService(
            payoutRepository, bridgeInitiation, confirmer, alerts, BridgeProperties(releaseGrace = Duration.ZERO),
        )
        stubState(BridgePaymentStatus.LINK_EXPIRED, transactionId = null, syncedAt = Instant.now())

        noGrace.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.releaseReservation(payout.id, any()) }
    }

    @Test
    fun `keeps the amount engaged on an intermediate status`() {
        stubState(BridgePaymentStatus.PDNG)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.recordInFlight(payout.id, BridgePaymentStatus.PDNG, "tx_1") }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    @Test
    fun `does not settle or fail on a PART status it cannot interpret`() {
        // A payout carries a single transaction, so PART should be impossible; guessing either way
        // would be worse than leaving it engaged for manual reconciliation.
        stubState(BridgePaymentStatus.PART)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.recordInFlight(payout.id, BridgePaymentStatus.PART, "tx_1") }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    @Test
    fun `alerts on a partial execution rather than logging it to nobody`() {
        // "Left engaged for manual reconciliation" is only true if somebody is told. Part of an
        // amount may have moved, and a log.error is not a reconciliation request.
        stubState(BridgePaymentStatus.PART)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { alerts.reportFailure(TechnicalAlertKind.PAYOUT_PARTIALLY_EXECUTED, any(), any(), any()) }
    }

    @Test
    fun `ignores a notification for a payment link this instance never created`() {
        // Bridge also notifies for links created by another environment sharing the sandbox app.
        every { payoutRepository.findRoutingByBridgePaymentLinkId("pl_unknown") } returns null

        service.handlePaymentLinkNotification("pl_unknown")

        verify(exactly = 0) { bridgeInitiation.getPaymentLink(any()) }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    @Test
    fun `resolves the payout via client_reference when payment_link_id is absent`() {
        // payment.transaction.* documents payment_link_id as optional, unlike payment.link.updated.
        stubState(BridgePaymentStatus.ACSC)
        every { payoutRepository.findRoutingById(payout.id) } returns routing()

        service.handlePaymentLinkNotification(null, payout.id.toString())

        verify { confirmer.finaliseSettled(payout.id, "tx_1") }
    }

    @Test
    fun `ignores a notification with neither a resolvable payment_link_id nor client_reference`() {
        every { payoutRepository.findRoutingByBridgePaymentLinkId("pl_unknown") } returns null

        service.handlePaymentLinkNotification("pl_unknown", "not-a-uuid")

        verify(exactly = 0) { bridgeInitiation.getPaymentLink(any()) }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
    }

    @Test
    fun `propagates an unreachable Bridge so the notification is retried`() {
        // Swallowing this would strand the payout PENDING with its balance engaged forever.
        every { payoutRepository.findRoutingByBridgePaymentLinkId(LINK_ID) } returns routing()
        every { bridgeInitiation.getPaymentLink(LINK_ID) } throws BadGatewayException("unreachable")

        assertThrows<BadGatewayException> { service.handlePaymentLinkNotification(LINK_ID) }

        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    private companion object {
        const val LINK_ID = "pl_123"
    }
}

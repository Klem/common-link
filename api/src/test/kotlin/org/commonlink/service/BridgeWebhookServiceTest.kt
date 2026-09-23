package org.commonlink.service

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
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

    private val service = BridgeWebhookService(payoutRepository, bridgeInitiation, confirmer, alerts)

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

    private fun stubState(
        status: BridgePaymentStatus,
        transactionId: String? = "tx_1",
        statusReason: String? = null,
    ) {
        every { payoutRepository.findRoutingByBridgePaymentLinkId(LINK_ID) } returns routing()
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

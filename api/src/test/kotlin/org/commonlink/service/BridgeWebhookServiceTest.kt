package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Payee
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.BadGatewayException
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
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

    private val service = BridgeWebhookService(payoutRepository, bridgeInitiation, confirmer)

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

    private fun stubState(
        status: BridgePaymentStatus,
        transactionId: String? = "tx_1",
        statusReason: String? = null,
    ) {
        every { payoutRepository.findByBridgePaymentLinkId(LINK_ID) } returns payout
        every { bridgeInitiation.getPaymentLink(LINK_ID) } returns
            BridgePaymentLinkState(status, transactionId, statusReason, null)
    }

    @Test
    fun `settles the payout when Bridge reports ACSC`() {
        stubState(BridgePaymentStatus.ACSC)

        service.handlePaymentLinkNotification(LINK_ID)

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

        service.handlePaymentLinkNotification(LINK_ID)

        verify {
            confirmer.finaliseFailed(
                payout.id, "debit_account_insufficient_funds", BridgePaymentStatus.RJCT,
            )
        }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
    }

    @Test
    fun `fails the payout when the authorisation window expired`() {
        stubState(BridgePaymentStatus.LINK_EXPIRED, transactionId = null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.finaliseFailed(payout.id, any(), BridgePaymentStatus.LINK_EXPIRED) }
    }

    @Test
    fun `fails the payout when the link was revoked`() {
        stubState(BridgePaymentStatus.LINK_REVOKED, transactionId = null)

        service.handlePaymentLinkNotification(LINK_ID)

        verify { confirmer.finaliseFailed(payout.id, any(), BridgePaymentStatus.LINK_REVOKED) }
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
        every { payoutRepository.findByBridgePaymentLinkId("pl_unknown") } returns null

        service.handlePaymentLinkNotification("pl_unknown")

        verify(exactly = 0) { bridgeInitiation.getPaymentLink(any()) }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    @Test
    fun `propagates an unreachable Bridge so the notification is retried`() {
        // Swallowing this would strand the payout PENDING with its balance engaged forever.
        every { payoutRepository.findByBridgePaymentLinkId(LINK_ID) } returns payout
        every { bridgeInitiation.getPaymentLink(LINK_ID) } throws BadGatewayException("unreachable")

        assertThrows<BadGatewayException> { service.handlePaymentLinkNotification(LINK_ID) }

        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
    }

    private companion object {
        const val LINK_ID = "pl_123"
    }
}

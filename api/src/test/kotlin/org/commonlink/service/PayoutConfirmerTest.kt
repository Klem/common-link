package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.Payee
import org.commonlink.entity.PayeeIban
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayeeIbanRepository
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional
import java.util.UUID

/**
 * Covers the confirmation phases that moved out of [PayoutService] when Bridge became responsible
 * for the actual transfer: the per-payout validations, the amount engagement taken under the
 * campaign lock, and the outcomes driven by the Bridge webhook.
 */
class PayoutConfirmerTest {

    private val payoutRepository    = mockk<PayoutRepository>()
    private val campaignRepository  = mockk<CampaignRepository>()
    private val payeeIbanRepository = mockk<PayeeIbanRepository>()
    private val donationRepository  = mockk<DonationRepository>()
    private val outbox              = mockk<OnchainOutboxService>()

    private val confirmer = PayoutConfirmer(
        payoutRepository, campaignRepository, payeeIbanRepository, donationRepository, outbox,
    )

    private val assocId    = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val ibanId     = UUID.randomUUID()

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val assoc = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789")
        .also { setId(it, assocId) }
    private val campaign = Campaign(association = assoc, name = "Camp", emoji = "🌍", description = "desc",
        goal = BigDecimal("10000"), status = CampaignStatus.LIVE)
        .also { setId(it, campaignId) }
    private val payee = Payee(association = assoc, name = "Payee", identifier1 = "123456789")
        .also { setId(it, UUID.randomUUID()) }
    private val verifiedIban = PayeeIban(payee = payee, iban = "FR7630006000011234567890189",
        status = IbanVerificationStatus.VERIFIED)
        .also { setId(it, ibanId) }

    private fun newPayout(
        status: PayoutStatus = PayoutStatus.PENDING,
        amount: String = "500",
        bridgeStatus: BridgePaymentStatus? = null,
    ) = Payout(
        campaign = campaign, payee = payee, payeeIbanId = ibanId, payeeIbanValue = verifiedIban.iban,
        amount = BigDecimal(amount), kind = PayoutKind.EXPENSE, typeCode = "60-mat",
        label = "Achat matériel pédagogique", status = status, bridgeStatus = bridgeStatus,
    )

    private fun setId(target: Any, id: UUID) {
        target.javaClass.getDeclaredField("id").also { it.isAccessible = true }.set(target, id)
    }

    private fun stubBalance(confirmed: String, inFlight: String = "0", raised: String) {
        every { payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED) } returns BigDecimal(confirmed)
        every { payoutRepository.sumInFlightAmountByCampaignId(campaignId) } returns BigDecimal(inFlight)
        every { donationRepository.sumConfirmedAmountByCampaignId(campaignId) } returns BigDecimal(raised)
    }

    // ── loadForConfirm ───────────────────────────────────────────────────────

    @Test
    fun `loadForConfirm - returns the transfer context for a valid PENDING payout`() {
        val payout = newPayout()
        every {
            payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payout.id, assocId)
        } returns payout
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(verifiedIban)

        val context = confirmer.loadForConfirm(campaignId, payout.id, assocId)

        assertThat(context.payoutId).isEqualTo(payout.id)
        assertThat(context.amount).isEqualByComparingTo("500")
        assertThat(context.payeeIban).isEqualTo(verifiedIban.iban)
        assertThat(context.payeeName).isEqualTo(payee.name)
        assertThat(context.campaignId).isEqualTo(campaignId)
    }

    @Test
    fun `loadForConfirm - unknown payout throws NotFoundException`() {
        val payoutId = UUID.randomUUID()
        every {
            payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payoutId, assocId)
        } returns null

        assertThrows<NotFoundException> { confirmer.loadForConfirm(campaignId, payoutId, assocId) }
    }

    @Test
    fun `loadForConfirm - already CONFIRMED throws ConflictException`() {
        val payout = newPayout(status = PayoutStatus.CONFIRMED)
        every {
            payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payout.id, assocId)
        } returns payout

        assertThrows<ConflictException> { confirmer.loadForConfirm(campaignId, payout.id, assocId) }
    }

    @Test
    fun `loadForConfirm - transfer already in progress throws ConflictException`() {
        // Guards against a double confirmation initiating two transfers for the same payout.
        val payout = newPayout(bridgeStatus = BridgePaymentStatus.CREA)
        every {
            payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payout.id, assocId)
        } returns payout

        assertThrows<ConflictException> { confirmer.loadForConfirm(campaignId, payout.id, assocId) }
    }

    @Test
    fun `loadForConfirm - IBAN downgraded after create throws ConflictException`() {
        val payout = newPayout()
        val downgraded = PayeeIban(payee = payee, iban = verifiedIban.iban, status = IbanVerificationStatus.NO_MATCH)
            .also { setId(it, ibanId) }
        every {
            payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payout.id, assocId)
        } returns payout
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(downgraded)

        assertThrows<ConflictException> { confirmer.loadForConfirm(campaignId, payout.id, assocId) }
    }

    @Test
    fun `loadForConfirm - IBAN disabled after create throws ConflictException`() {
        val payout = newPayout()
        val disabled = PayeeIban(payee = payee, iban = verifiedIban.iban,
            status = IbanVerificationStatus.VERIFIED, active = false)
            .also { setId(it, ibanId) }
        every {
            payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payout.id, assocId)
        } returns payout
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(disabled)

        assertThrows<ConflictException> { confirmer.loadForConfirm(campaignId, payout.id, assocId) }
    }

    // ── reserve ──────────────────────────────────────────────────────────────

    @Test
    fun `reserve - engages the amount under the campaign lock`() {
        val payout = newPayout()
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payoutRepository.findById(payout.id) } returns Optional.of(payout)
        stubBalance(confirmed = "0", raised = "1000")
        every { payoutRepository.save(payout) } returns payout

        confirmer.reserve(campaignId, payout.id)

        assertThat(payout.bridgeStatus).isEqualTo(BridgePaymentStatus.CREA)
        assertThat(payout.bridgeSyncedAt).isNotNull()
        assertThat(payout.status).isEqualTo(PayoutStatus.PENDING)
    }

    @Test
    fun `reserve - balance consumed by confirmed payouts throws ConflictException`() {
        // 500 requested, only 100 confirmable (1000 raised − 900 already confirmed).
        val payout = newPayout()
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payoutRepository.findById(payout.id) } returns Optional.of(payout)
        stubBalance(confirmed = "900", raised = "1000")

        assertThrows<ConflictException> { confirmer.reserve(campaignId, payout.id) }
        assertThat(payout.bridgeStatus).isNull()
    }

    @Test
    fun `reserve - balance already committed to an in-flight transfer throws ConflictException`() {
        // The concurrency case the engagement marker exists for: another confirmation has
        // initiated a transfer not yet promoted to CONFIRMED, so its amount is already spent.
        val payout = newPayout()
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payoutRepository.findById(payout.id) } returns Optional.of(payout)
        stubBalance(confirmed = "0", inFlight = "900", raised = "1000")

        assertThrows<ConflictException> { confirmer.reserve(campaignId, payout.id) }
    }

    @Test
    fun `reserve - missing campaign throws NotFoundException`() {
        every { campaignRepository.findByIdForUpdate(campaignId) } returns null

        assertThrows<NotFoundException> { confirmer.reserve(campaignId, UUID.randomUUID()) }
    }

    // ── finalise ─────────────────────────────────────────────────────────────

    @Test
    fun `attachPaymentLink - stores the authorisation url and leaves the payout PENDING`() {
        val payout = newPayout(bridgeStatus = BridgePaymentStatus.CREA)
        every { payoutRepository.findById(payout.id) } returns Optional.of(payout)
        every { payoutRepository.save(payout) } returns payout

        val result = confirmer.attachPaymentLink(
            payout.id, BridgePaymentLink("pl_1", "https://pay.bridgeapi.io/link/abc"),
        )

        assertThat(result.status).isEqualTo(PayoutStatus.PENDING)
        assertThat(result.bridgePaymentLinkId).isEqualTo("pl_1")
        assertThat(result.bridgeCheckoutUrl).isEqualTo("https://pay.bridgeapi.io/link/abc")
        // Nothing is settled yet, so no attestation may exist.
        verify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
    }

    @Test
    fun `finaliseSettled - confirms the payout and enqueues the attestation with exact cents`() {
        // Regression: amount.toLong() * 100 truncated the decimals first, recording 10.50 EUR as
        // 1000 cents on-chain instead of 1050.
        val payout = newPayout(amount = "10.50", bridgeStatus = BridgePaymentStatus.PDNG)
        val jobId = UUID.randomUUID()
        val job = mockk<OnchainJob>()
        val payloadSlot = slot<Any>()

        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout
        every { payoutRepository.save(payout) } returns payout
        every { job.id } returns jobId
        every {
            outbox.enqueue(OnchainJobAction.RECORD_PAYOUT, capture(payloadSlot), "PAYOUT:${payout.id}")
        } returns job

        val result = confirmer.finaliseSettled(payout.id, "tx_1")

        assertThat(result.status).isEqualTo(PayoutStatus.CONFIRMED)
        assertThat(result.bridgeStatus).isEqualTo(BridgePaymentStatus.ACSC)
        assertThat(result.bridgePaymentTransactionId).isEqualTo("tx_1")
        assertThat(result.onchainJobId).isEqualTo(jobId)
        assertThat((payloadSlot.captured as RecordPayoutPayload).amountCents)
            .isEqualTo(BigInteger.valueOf(1050))
    }

    @Test
    fun `finaliseSettled - a webhook replay does not re-enqueue the attestation`() {
        // Bridge retries a notification for up to two days, so the same settlement arrives twice.
        val payout = newPayout(status = PayoutStatus.CONFIRMED, bridgeStatus = BridgePaymentStatus.ACSC)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout

        confirmer.finaliseSettled(payout.id, "tx_1")

        // The guard only holds under the row lock: Bridge delivers its notifications in parallel,
        // and an unlocked read let two threads both settle and both enqueue, the second dying on
        // the outbox unique constraint and answering 502 for a settlement that had succeeded.
        verify(exactly = 0) { payoutRepository.findById(any()) }

        verify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
        verify(exactly = 0) { payoutRepository.save(any()) }
    }

    @Test
    fun `discardNeverInitiated - drops a payout Bridge never accepted`() {
        // No link, no authorisation URL, nothing a later notification could refer to: the row would
        // record only that a form failed to submit, and an association unable to tell that apart
        // from a real attempt simply creates another one.
        val payout = newPayout(bridgeStatus = BridgePaymentStatus.CREA)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout
        every { payoutRepository.delete(payout) } returns Unit

        confirmer.discardNeverInitiated(payout.id)

        verify { payoutRepository.delete(payout) }
    }

    @Test
    fun `discardNeverInitiated - never drops a payout that has a Bridge link`() {
        // A link that exists is a fact this row is the only local record of — a destination
        // read-back refused is the clearest case, and it is evidence of a documented control.
        val payout = newPayout(bridgeStatus = BridgePaymentStatus.CREA)
        payout.bridgePaymentLinkId = "pl_1"
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout

        confirmer.discardNeverInitiated(payout.id)

        verify(exactly = 0) { payoutRepository.delete(any<Payout>()) }
    }

    @Test
    fun `discardNeverInitiated - never drops a payout that already left PENDING`() {
        val payout = newPayout(status = PayoutStatus.CONFIRMED, bridgeStatus = BridgePaymentStatus.ACSC)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout

        confirmer.discardNeverInitiated(payout.id)

        verify(exactly = 0) { payoutRepository.delete(any<Payout>()) }
    }

    @Test
    fun `releaseReservation - returns the payout to a confirmable state, keeping the diagnostic`() {
        // A Bridge refusal before any link exists must not retire the payout: loadForConfirm only
        // accepts PENDING with a null bridgeStatus, so a FAILED stamp here would be terminal and
        // the association could never retry. Clearing bridgeStatus also returns the amount to the
        // confirmable balance, since sumInFlightAmount counts exactly the non-null ones.
        val payout = newPayout(bridgeStatus = BridgePaymentStatus.CREA)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout
        every { payoutRepository.save(payout) } returns payout

        confirmer.releaseReservation(payout.id, "Bridge payment initiation unavailable: timeout")

        assertThat(payout.status).isEqualTo(PayoutStatus.PENDING)
        assertThat(payout.bridgeStatus).isNull()
        assertThat(payout.bridgeLastError).isEqualTo("Bridge payment initiation unavailable: timeout")
        verify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
    }

    @Test
    fun `releaseReservation - drops the dead checkout url and leaves the payout confirmable again`() {
        // The expiry path: the association never authenticated, the link died, and the URL on the
        // row now leads nowhere. Leaving it would offer a dead end; leaving bridgeStatus set would
        // keep the amount engaged on the campaign for ever.
        val payout = newPayout(bridgeStatus = BridgePaymentStatus.CREA)
        payout.bridgePaymentLinkId = "pl_1"
        payout.bridgeCheckoutUrl = "https://pay.bridgeapi.io/link/dead"
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout
        every { payoutRepository.save(payout) } returns payout

        confirmer.releaseReservation(payout.id, "Bank authorisation window expired before the transfer was authorised")

        assertThat(payout.bridgeCheckoutUrl).isNull()
        // Kept: the audit trail of the attempt, and the routing key for a notification still in
        // flight for that link.
        assertThat(payout.bridgePaymentLinkId).isEqualTo("pl_1")

        // The state loadForConfirm demands — proven by running it rather than by asserting fields.
        every {
            payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payout.id, assocId)
        } returns payout
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(verifiedIban)

        val context = confirmer.loadForConfirm(campaignId, payout.id, assocId)

        assertThat(context.payoutId).isEqualTo(payout.id)
    }

    @Test
    fun `releaseReservation - never touches a payout that already left PENDING`() {
        val payout = newPayout(status = PayoutStatus.CONFIRMED, bridgeStatus = BridgePaymentStatus.ACSC)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout

        confirmer.releaseReservation(payout.id, "late failure")

        assertThat(payout.status).isEqualTo(PayoutStatus.CONFIRMED)
        assertThat(payout.bridgeStatus).isEqualTo(BridgePaymentStatus.ACSC)
        verify(exactly = 0) { payoutRepository.save(any()) }
    }

    @Test
    fun `releaseReservation - never resurrects a payout the bank refused`() {
        // Observed live on 2026-09-24: our own revocation of a rejected link comes back as
        // LINK_REVOKED, whose arm releases. Without this guard the payout just failed would return
        // to a retryable PENDING and its amount be counted available a second time.
        val payout = newPayout(status = PayoutStatus.FAILED, bridgeStatus = BridgePaymentStatus.RJCT)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout

        confirmer.releaseReservation(payout.id, "Payment link revoked before the transfer was authorised")

        assertThat(payout.status).isEqualTo(PayoutStatus.FAILED)
        assertThat(payout.bridgeStatus).isEqualTo(BridgePaymentStatus.RJCT)
        verify(exactly = 0) { payoutRepository.save(any()) }
    }

    @Test
    fun `finaliseFailed - fails the payout without emitting any attestation`() {
        val payout = newPayout(bridgeStatus = BridgePaymentStatus.CREA)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout
        every { payoutRepository.save(payout) } returns payout

        confirmer.finaliseFailed(payout.id, "debit_account_insufficient_funds", BridgePaymentStatus.RJCT)

        assertThat(payout.status).isEqualTo(PayoutStatus.FAILED)
        assertThat(payout.bridgeStatus).isEqualTo(BridgePaymentStatus.RJCT)
        assertThat(payout.bridgeLastError).isEqualTo("debit_account_insufficient_funds")
        verify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
    }

    @Test
    fun `finaliseFailed - refuses to un-settle a payout already confirmed`() {
        // A late RJCT after an ACSC must not retract a payout whose attestation is already public.
        val payout = newPayout(status = PayoutStatus.CONFIRMED, bridgeStatus = BridgePaymentStatus.ACSC)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout

        confirmer.finaliseFailed(payout.id, "late rejection", BridgePaymentStatus.RJCT)

        assertThat(payout.status).isEqualTo(PayoutStatus.CONFIRMED)
        verify(exactly = 0) { payoutRepository.save(any()) }
    }

    @Test
    fun `recordInFlight - keeps the amount engaged without confirming the payout`() {
        val payout = newPayout(bridgeStatus = BridgePaymentStatus.CREA)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout
        every { payoutRepository.save(payout) } returns payout

        confirmer.recordInFlight(payout.id, BridgePaymentStatus.PDNG, "tx_1")

        // Still PENDING with a non-null bridgeStatus — the two conditions sumInFlightAmount uses,
        // so the amount stays counted as spent instead of returning to the balance.
        assertThat(payout.status).isEqualTo(PayoutStatus.PENDING)
        assertThat(payout.bridgeStatus).isEqualTo(BridgePaymentStatus.PDNG)
        assertThat(payout.bridgePaymentTransactionId).isEqualTo("tx_1")
        verify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
    }

    @Test
    fun `recordInFlight - refuses to downgrade a payout already settled`() {
        // Bridge fires payment.transaction.updated and payment.link.updated concurrently: the
        // second thread can still read PDNG after the first one settled the payout. Writing it
        // would leave a CONFIRMED payout displaying "in progress" for good.
        val payout = newPayout(status = PayoutStatus.CONFIRMED, bridgeStatus = BridgePaymentStatus.ACSC)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout

        confirmer.recordInFlight(payout.id, BridgePaymentStatus.PDNG, "tx_1")

        assertThat(payout.status).isEqualTo(PayoutStatus.CONFIRMED)
        assertThat(payout.bridgeStatus).isEqualTo(BridgePaymentStatus.ACSC)
        verify(exactly = 0) { payoutRepository.save(any()) }
    }

    @Test
    fun `recordInFlight - refuses to stamp an in-flight status over a bank refusal`() {
        // A link carrying two payment requests still reports an in-flight one after the other was
        // rejected. On 2026-09-24 such a notification landed 214 ms after the payout was failed,
        // and missed this branch only because the rejection had already revoked the link. FAILED
        // is terminal — loadForConfirm refuses it — so an in-flight status on that row describes a
        // transfer that is no longer being decided.
        val payout = newPayout(status = PayoutStatus.FAILED, bridgeStatus = BridgePaymentStatus.RJCT)
        every { payoutRepository.findByIdForUpdate(payout.id) } returns payout

        confirmer.recordInFlight(payout.id, BridgePaymentStatus.ACTC, "tx_1")

        assertThat(payout.status).isEqualTo(PayoutStatus.FAILED)
        assertThat(payout.bridgeStatus).isEqualTo(BridgePaymentStatus.RJCT)
        verify(exactly = 0) { payoutRepository.save(any()) }
    }
}

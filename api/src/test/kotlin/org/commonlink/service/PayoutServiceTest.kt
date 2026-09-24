package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.CreatePayoutRequest
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.BridgeInitiationNotStartedException
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.Payee
import org.commonlink.entity.PayeeIban
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayeeIbanRepository
import org.commonlink.repository.PayeeRepository
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class PayoutServiceTest {

    private val payoutRepository              = mockk<PayoutRepository>()
    private val campaignRepository            = mockk<CampaignRepository>()
    private val associationProfileRepository  = mockk<AssociationProfileRepository>()
    private val payeeRepository               = mockk<PayeeRepository>()
    private val payeeIbanRepository           = mockk<PayeeIbanRepository>()
    private val donationRepository            = mockk<DonationRepository>()
    private val confirmer                     = mockk<PayoutConfirmer>()
    private val bridgeInitiation              = mockk<BridgePaymentInitiationService>()

    private val service = PayoutService(
        payoutRepository, campaignRepository, associationProfileRepository,
        payeeRepository, payeeIbanRepository, donationRepository, confirmer,
        bridgeInitiation, FRONTEND_URL, MockEnvironment()
    )

    /** Same service under the prod profile, where a simulated payout must not be offered. */
    private val prodService = PayoutService(
        payoutRepository, campaignRepository, associationProfileRepository,
        payeeRepository, payeeIbanRepository, donationRepository, confirmer,
        bridgeInitiation, FRONTEND_URL, MockEnvironment().apply { setActiveProfiles("prod") }
    )

    private val userId     = UUID.randomUUID()   // JWT subject (User.id)
    private val assocId    = UUID.randomUUID()   // AssociationProfile.id
    private val campaignId = UUID.randomUUID()
    private val payeeId   = UUID.randomUUID()
    private val ibanId    = UUID.randomUUID()
    private val payoutId  = UUID.randomUUID()

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val assoc     = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789")
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, assocId) }

    private val campaign  = Campaign(association = assoc, name = "Camp", emoji = "🌍", description = "desc", goal = BigDecimal("10000"), status = CampaignStatus.LIVE)
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, campaignId) }

    private val payee = Payee(association = assoc, name = "Payee", identifier1 = "123456789")
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, payeeId) }

    private val payeeIban = PayeeIban(payee = payee, iban = "FR7630006000011234567890189", status = IbanVerificationStatus.VERIFIED)
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, ibanId) }

    private val pendingPayout = Payout(campaign = campaign, payee = payee, payeeIbanId = ibanId, payeeIbanValue = payeeIban.iban,
        amount = BigDecimal("500"), kind = PayoutKind.EXPENSE, typeCode = "60-mat", label = "Achat matériel", status = PayoutStatus.PENDING)

    /** Stubs the confirmed + pending payout sums used by the balance calculations. */
    private fun stubBalance(confirmed: String, pending: String = "0", raised: String) {
        every { payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED) } returns BigDecimal(confirmed)
        every { payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.PENDING) } returns BigDecimal(pending)
        every { donationRepository.sumConfirmedAmountByCampaignId(campaignId) } returns BigDecimal(raised)
    }

    /** A payee belonging to a different association, used to pin the ownership checks. */
    private val foreignPayee = Payee(
        association = AssociationProfile(
            user = User(email = "b@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK),
            name = "Autre asso", identifier = "987654321",
        ).also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, UUID.randomUUID()) },
        name = "Foreign Payee", identifier1 = "987654321",
    ).also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, UUID.randomUUID()) }

    private val foreignIban = PayeeIban(
        payee = foreignPayee, iban = "FR7630006000011111111111111", status = IbanVerificationStatus.VERIFIED,
    ).also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, UUID.randomUUID()) }

    @Test
    fun `create - refuses a payee belonging to another association`() {
        // The campaign is scoped, the payee was not. Naming a foreign payee echoed its name and
        // full IBAN back in the 201, and a confirmation would have sent this campaign's funds to an
        // IBAN never registered nor VOP-verified under this association.
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payeeRepository.findById(foreignPayee.id!!) } returns Optional.of(foreignPayee)

        val request = CreatePayoutRequest(
            payeeId = foreignPayee.id, payeeIbanId = foreignIban.id, amount = BigDecimal("10"),
            kind = PayoutKind.EXPENSE, typeCode = "60-mat", label = "Achat matériel pédagogique",
        )

        // "Not found", not "forbidden": the endpoint must disclose nothing about other associations.
        assertThrows<NotFoundException> { service.create(campaignId, request, userId) }
        verify(exactly = 0) { payoutRepository.save(any()) }
    }

    @Test
    fun `computeBlockingReasons - refuses an IBAN belonging to another association`() {
        // Unscoped, this endpoint answered 200 for a foreign IBAN and 404 otherwise, which makes it
        // an oracle for the existence and verification status of any IBAN on the platform.
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { payeeIbanRepository.findById(foreignIban.id!!) } returns Optional.of(foreignIban)

        assertThrows<NotFoundException> {
            service.computeBlockingReasons(campaignId, foreignIban.id!!, BigDecimal("10"), "Achat matériel pédagogique", userId)
        }
    }

    @Test
    fun `create - happy path returns PayoutDto`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(payeeIban)
        stubBalance(confirmed = "0", raised = "1000")
        every { payoutRepository.save(any()) } returnsArgument 0

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Achat matériel pédagogique")
        val result = service.create(campaignId, request, userId)

        assertThat(result.amount).isEqualByComparingTo("500")
        assertThat(result.status).isEqualTo(PayoutStatus.PENDING)
    }

    @Test
    fun `create - derives the kind from the accounting code, ignoring the one in the body`() {
        // Replaying the call with typeCode = "64-rem" and kind = EXPENSE filed a salary as an
        // operating cost. The code is what the association picked from a closed list; the kind was
        // only ever a projection of it computed in the browser.
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(payeeIban)
        stubBalance(confirmed = "0", raised = "1000")
        every { payoutRepository.save(any()) } returnsArgument 0

        val forged = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "64-rem", "Salaire coordinateur projet")
        assertThat(service.create(campaignId, forged, userId).kind).isEqualTo(PayoutKind.REMUNERATION)

        // And the converse, so the derivation is not simply pinned to one value.
        val alsoForged = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.REMUNERATION, "60-mat", "Achat matériel pédagogique")
        assertThat(service.create(campaignId, alsoForged, userId).kind).isEqualTo(PayoutKind.EXPENSE)

        // A code the association typed itself is an operational expense, like the frontend's own
        // mapping — only the closed 64-* set is personnel.
        val custom = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.REMUNERATION, "Frais divers", "Achat matériel pédagogique")
        assertThat(service.create(campaignId, custom, userId).kind).isEqualTo(PayoutKind.EXPENSE)
    }

    @Test
    fun `create - refused in prod while Bridge runs in demo mode`() {
        // Mirror of the disabled submit button: every click is replayable, so the server must
        // refuse too — otherwise a crafted request produces, in production, a payout that would
        // be reported as settled without any bank ever executing a transfer.
        every { bridgeInitiation.isDemoMode } returns true

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Achat matériel pédagogique")
        assertThrows<ConflictException> { prodService.create(campaignId, request, userId) }

        // Refused before anything is read or written: no repository is even touched.
        verify(exactly = 0) { payoutRepository.save(any()) }
        verify(exactly = 0) { associationProfileRepository.findByUserId(any()) }
    }

    @Test
    fun `create - allowed in prod once demo mode is off`() {
        every { bridgeInitiation.isDemoMode } returns false
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(payeeIban)
        stubBalance(confirmed = "0", raised = "1000")
        every { payoutRepository.save(any()) } returnsArgument 0

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Achat matériel pédagogique")

        assertThat(prodService.create(campaignId, request, userId).status).isEqualTo(PayoutStatus.PENDING)
    }

    @Test
    fun `confirm - refused in prod while Bridge runs in demo mode`() {
        // A payout created before the environment was closed must not become settleable either:
        // confirm is where the simulated link is produced and the payout reported as paid.
        every { bridgeInitiation.isDemoMode } returns true

        assertThrows<ConflictException> { prodService.confirm(campaignId, payoutId, userId) }

        verify(exactly = 0) { confirmer.loadForConfirm(any(), any(), any()) }
        verify(exactly = 0) {
            bridgeInitiation.createPaymentLink(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `create - unverified IBAN throws ConflictException`() {
        val unverifiedIban = PayeeIban(payee = payee, iban = "FR7630006000011234567890189", status = IbanVerificationStatus.PENDING)
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, ibanId) }

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(unverifiedIban)
        stubBalance(confirmed = "0", raised = "1000")

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Achat matériel pédagogique")
        assertThrows<ConflictException> { service.create(campaignId, request, userId) }
    }

    @Test
    fun `create - amount exceeds available balance throws ConflictException`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(payeeIban)
        stubBalance(confirmed = "900", raised = "1000")

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Achat matériel pédagogique")
        assertThrows<ConflictException> { service.create(campaignId, request, userId) }
    }

    @Test
    fun `create - PENDING payouts are reserved against available balance (H2)`() {
        // Raised 1000, none confirmed, but 600 already reserved by a PENDING payout → only 400 free.
        // A second 500 payout must be blocked; before the fix it saw the full 1000 and was allowed.
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(payeeIban)
        stubBalance(confirmed = "0", pending = "600", raised = "1000")

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Achat matériel pédagogique")
        assertThrows<ConflictException> { service.create(campaignId, request, userId) }
    }

    @Test
    fun `create - wrong association returns NotFoundException`() {
        val wrongUserId  = UUID.randomUUID()
        val wrongAssocId = UUID.randomUUID()
        val wrongAssoc   = AssociationProfile(user = assocUser, name = "Other", identifier = "999999999")
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, wrongAssocId) }
        every { associationProfileRepository.findByUserId(wrongUserId) } returns Optional.of(wrongAssoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Motif test create wrong")
        assertThrows<NotFoundException> { service.create(campaignId, request, wrongUserId) }
    }

    @Test
    fun `create - IBAN does not belong to payee throws NotFoundException`() {
        val otherPayee = Payee(association = assoc, name = "Other", identifier1 = "987654321")
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, UUID.randomUUID()) }
        val ibanFromOtherPayee = PayeeIban(payee = otherPayee, iban = "DE89370400440532013000")
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, ibanId) }

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findByIdForUpdate(campaignId) } returns campaign
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(ibanFromOtherPayee)

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Motif test iban mismatch")
        assertThrows<NotFoundException> { service.create(campaignId, request, userId) }
    }

    /**
     * Stubs phase 1 of confirmation. The per-payout validations it performs (status, IBAN,
     * balance under lock) are covered by [PayoutConfirmerTest] — here the confirmer is a mock,
     * so this test class only asserts how [PayoutService] sequences the phases.
     */
    private fun stubLoadForConfirm() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { confirmer.loadForConfirm(campaignId, payoutId, assocId) } returns PayoutConfirmContext(
            payoutId = pendingPayout.id,
            campaignId = campaignId,
            amount = pendingPayout.amount,
            label = pendingPayout.label,
            payerName = assoc.name,
            payerReference = assocId.toString(),
            payeeName = payee.name,
            payeeIban = payeeIban.iban,
        )
    }

    @Test
    fun `confirm - initiates the transfer and returns the bank authorisation url`() {
        val link = BridgePaymentLink("pl_1", "https://pay.bridgeapi.io/link/abc")
        val awaiting = Payout(
            campaign = campaign, payee = payee, payeeIbanId = ibanId, payeeIbanValue = payeeIban.iban,
            amount = BigDecimal("500"), kind = PayoutKind.EXPENSE, typeCode = "60-mat",
            label = "Achat matériel", status = PayoutStatus.PENDING,
            bridgePaymentLinkId = link.id, bridgeCheckoutUrl = link.url,
            bridgeStatus = BridgePaymentStatus.CREA,
        )

        stubLoadForConfirm()
        every { confirmer.reserve(campaignId, payoutId) } returns Unit
        every {
            bridgeInitiation.createPaymentLink(
                payoutId = payoutId,
                payerName = assoc.name,
                payerReference = assocId.toString(),
                payeeName = payee.name,
                payeeIban = payeeIban.iban,
                amount = BigDecimal("500"),
                label = "Achat matériel",
                senderIban = null,
                callbackUrl = any(),
            )
        } returns link
        every { confirmer.attachPaymentLink(payoutId, link) } returns awaiting

        val result = service.confirm(campaignId, payoutId, userId)

        // Still PENDING: nothing moves until the association authorises at its own bank.
        assertThat(result.status).isEqualTo(PayoutStatus.PENDING)
        assertThat(result.bridgeCheckoutUrl).isEqualTo(link.url)
        // The amount must be engaged before the initiation exists, never after.
        verifyOrder {
            confirmer.reserve(campaignId, payoutId)
            bridgeInitiation.createPaymentLink(any(), any(), any(), any(), any(), any(), any(), any(), any())
            confirmer.attachPaymentLink(payoutId, link)
        }
        // The attestation belongs to settlement, which only the webhook can establish.
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
    }

    @Test
    fun `confirm - drops the payout when Bridge never accepted the initiation`() {
        // No link exists, so the row records nothing but a form that failed to submit. Four
        // identical rows appeared from a single payment on 2026-09-23 because the association could
        // not tell that apart from a real attempt.
        stubLoadForConfirm()
        every { confirmer.reserve(campaignId, payoutId) } returns Unit
        every {
            bridgeInitiation.createPaymentLink(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws BridgeInitiationNotStartedException("Bridge payment initiation unavailable: 400 Bad Request")
        every { confirmer.discardNeverInitiated(payoutId) } returns Unit

        assertThrows<BridgeInitiationNotStartedException> { service.confirm(campaignId, payoutId, userId) }

        verify { confirmer.discardNeverInitiated(payoutId) }
        verify(exactly = 0) { confirmer.releaseReservation(any(), any()) }
    }

    @Test
    fun `confirm - keeps the payout when a link may exist`() {
        // The destination read-back refusal lands here: a link was created and revoked, and this
        // row is the only local record that it existed at all.
        stubLoadForConfirm()
        every { confirmer.reserve(campaignId, payoutId) } returns Unit
        every {
            bridgeInitiation.createPaymentLink(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws BadGatewayException("Bridge recorded a different destination IBAN — transfer refused")
        every { confirmer.releaseReservation(payoutId, any()) } returns Unit

        assertThrows<BadGatewayException> { service.confirm(campaignId, payoutId, userId) }

        verify { confirmer.releaseReservation(payoutId, any()) }
        verify(exactly = 0) { confirmer.discardNeverInitiated(any()) }
    }

    @Test
    fun `confirm - sends the campaign payments tab and the payout as the bank return url`() {
        val link = BridgePaymentLink("pl_1", "https://pay.bridgeapi.io/link/abc")
        val callbackSlot = slot<String>()

        stubLoadForConfirm()
        every { confirmer.reserve(campaignId, payoutId) } returns Unit
        every {
            bridgeInitiation.createPaymentLink(any(), any(), any(), any(), any(), any(), any(), any(), capture(callbackSlot))
        } returns link
        every { confirmer.attachPaymentLink(payoutId, link) } returns pendingPayout

        service.confirm(campaignId, payoutId, userId)

        // Both matter. Without the tab the association lands on Infos, hiding the row it came back
        // to see; without the payout the page cannot watch that one settle, and the association
        // returns while Bridge is still notifying — CREA, ACTC and PDNG landed within 24 seconds of
        // each other on 2026-09-22.
        assertThat(callbackSlot.captured)
            .isEqualTo("$FRONTEND_URL/fr/dashboard/association/campaigns/$campaignId?tab=payments&payout=$payoutId")
    }

    @Test
    fun `confirm - unreachable Bridge releases the reservation and emits no attestation`() {
        // The payout must stay PENDING: without the authorisation URL no debit can ever happen, so
        // releasing cannot lead to a double payment — and failing it would be terminal, because
        // loadForConfirm only accepts PENDING.
        stubLoadForConfirm()
        every { confirmer.reserve(campaignId, payoutId) } returns Unit
        every { bridgeInitiation.createPaymentLink(any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            BadGatewayException("Bridge payment initiation unavailable: timeout")
        every { confirmer.releaseReservation(payoutId, any()) } returns Unit

        assertThrows<BadGatewayException> { service.confirm(campaignId, payoutId, userId) }

        verify { confirmer.releaseReservation(payoutId, any()) }
        verify(exactly = 0) { confirmer.finaliseFailed(any(), any(), any()) }
        verify(exactly = 0) { confirmer.finaliseSettled(any(), any()) }
        verify(exactly = 0) { confirmer.attachPaymentLink(any(), any()) }
    }

    @Test
    fun `getSummary - returns correct aggregates with PENDING reserved`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        stubBalance(confirmed = "1000", pending = "200", raised = "5000")
        every { payoutRepository.countByCampaignId(campaignId) } returns 5L
        every { payoutRepository.countByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED) } returns 3L
        every { bridgeInitiation.isDemoMode } returns false

        val summary = service.getSummary(campaignId, userId)

        assertThat(summary.confirmedAmount).isEqualByComparingTo("1000")
        assertThat(summary.pendingAmount).isEqualByComparingTo("200")
        assertThat(summary.txTotal).isEqualTo(5L)
        assertThat(summary.availableBalance).isEqualByComparingTo("3800") // 5000 - 1000 confirmed - 200 pending
        assertThat(summary.paymentsEnabled).isTrue()
    }

    @Test
    fun `getSummary - paymentsEnabled is false in prod while Bridge runs in demo mode`() {
        // The Payments tab disables its submit button on this flag: in demo mode the transfer is
        // simulated and reported as settled, so an enabled button would claim to a real
        // association a payment that no bank ever executed.
        stubSummaryQueries()
        every { bridgeInitiation.isDemoMode } returns true

        assertThat(prodService.getSummary(campaignId, userId).paymentsEnabled).isFalse()
    }

    @Test
    fun `getSummary - paymentsEnabled stays true outside prod while Bridge runs in demo mode`() {
        // Local and staging must keep exercising the payout journey without Bridge credentials —
        // that is what demo mode exists for.
        stubSummaryQueries()
        every { bridgeInitiation.isDemoMode } returns true

        assertThat(service.getSummary(campaignId, userId).paymentsEnabled).isTrue()
    }

    @Test
    fun `getSummary - paymentsEnabled is true in prod once demo mode is off`() {
        stubSummaryQueries()
        every { bridgeInitiation.isDemoMode } returns false

        assertThat(prodService.getSummary(campaignId, userId).paymentsEnabled).isTrue()
    }

    /** Minimal stubbing for a [PayoutService.getSummary] call on an empty campaign. */
    private fun stubSummaryQueries() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        stubBalance(confirmed = "0", pending = "0", raised = "1000")
        every { payoutRepository.countByCampaignId(campaignId) } returns 0L
        every { payoutRepository.countByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED) } returns 0L
    }

    @Test
    fun `computeBlockingReasons - VERIFIED iban and sufficient balance returns no reasons`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(payeeIban)
        stubBalance(confirmed = "0", raised = "1000")

        val reasons = service.computeBlockingReasons(campaignId, ibanId, BigDecimal("500"), "Achat matériel pédagogique", userId)

        assertThat(reasons).isEmpty()
    }

    @Test
    fun `computeBlockingReasons - unverified iban and insufficient balance returns both reasons`() {
        val unverifiedIban = PayeeIban(payee = payee, iban = "FR7630006000011234567890189", status = IbanVerificationStatus.NO_MATCH)
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, ibanId) }

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(unverifiedIban)
        stubBalance(confirmed = "900", raised = "1000")

        val reasons = service.computeBlockingReasons(campaignId, ibanId, BigDecimal("500"), "Achat matériel pédagogique", userId)

        assertThat(reasons).containsExactlyInAnyOrder(
            org.commonlink.entity.PayoutBlockingReason.IBAN_NOT_VERIFIED,
            org.commonlink.entity.PayoutBlockingReason.INSUFFICIENT_BALANCE,
        )
    }

    @Test
    fun `computeBlockingReasons - disabled VERIFIED iban returns IBAN_NOT_VERIFIED`() {
        val disabledIban = PayeeIban(payee = payee, iban = "FR7630006000011234567890189", status = IbanVerificationStatus.VERIFIED, active = false)
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, ibanId) }

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(disabledIban)
        stubBalance(confirmed = "0", raised = "1000")

        val reasons = service.computeBlockingReasons(campaignId, ibanId, BigDecimal("500"), "Achat matériel pédagogique", userId)

        assertThat(reasons).containsExactly(org.commonlink.entity.PayoutBlockingReason.IBAN_NOT_VERIFIED)
    }

    @Test
    fun `computeBlockingReasons - label under 16 chars returns DESCRIPTION_TOO_SHORT`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(payeeIban)
        stubBalance(confirmed = "0", raised = "1000")

        val reasons = service.computeBlockingReasons(campaignId, ibanId, BigDecimal("500"), "trop court", userId)

        assertThat(reasons).containsExactly(org.commonlink.entity.PayoutBlockingReason.DESCRIPTION_TOO_SHORT)
    }

    private companion object {
        const val FRONTEND_URL = "http://localhost:3000"
    }
}

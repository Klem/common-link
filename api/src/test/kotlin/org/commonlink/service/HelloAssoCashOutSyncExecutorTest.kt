package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.HelloAssoCashOutProperties
import org.commonlink.config.PaymentsProperties
import org.commonlink.dto.HelloAssoPaymentDetailJson
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoCashOutSync
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.repository.HelloAssoCashOutSyncRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class HelloAssoCashOutSyncExecutorTest {

    private val fundsRepo = mockk<HelloAssoDonationFundsRepository>()
    private val syncRepo = mockk<HelloAssoCashOutSyncRepository>()
    private val connectionRepo = mockk<HelloAssoConnectionRepository>()
    private val tokenManager = mockk<HelloAssoTokenManager>()
    private val breaker = mockk<HelloAssoConnectionBreaker>(relaxed = true)
    private val gateway = mockk<HelloAssoGateway>()
    private val alerts = mockk<TechnicalAlertService>(relaxed = true)
    private val auditLog = mockk<ComplianceAuditLogService>(relaxed = true)
    private val fundsService = HelloAssoDonationFundsService(fundsRepo, alerts, auditLog)
    private fun executorWith(settings: HelloAssoCashOutProperties) = HelloAssoCashOutSyncExecutor(
        fundsRepo, syncRepo, connectionRepo, tokenManager, breaker, gateway, fundsService, alerts,
        PaymentsProperties(helloasso = settings), TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
    )
    private var executor = executorWith(HelloAssoCashOutProperties())

    private val assocA = UUID.randomUUID()
    private val assocB = UUID.randomUUID()
    private val syncs = mutableMapOf<UUID, HelloAssoCashOutSync>()
    private val rows = mutableMapOf<UUID, HelloAssoDonationFunds>()

    init {
        every { syncRepo.insertIfMissing(any()) } answers { syncs.getOrPut(firstArg()) { HelloAssoCashOutSync(firstArg()) }; 1 }
        every { syncRepo.findById(any()) } answers { Optional.ofNullable(syncs[firstArg()]) }
        every { syncRepo.save(any<HelloAssoCashOutSync>()) } answers { firstArg() }
        every { syncRepo.claimRefresh(any(), any(), any()) } answers { syncs[firstArg()]?.lastRefreshRequestedAt = secondArg(); 1 }
        every { fundsRepo.save(any<HelloAssoDonationFunds>()) } answers { firstArg() }
        every { fundsRepo.findByDonationIdForUpdate(any()) } answers { rows[firstArg()] }
        every { fundsRepo.existsTrackable() } answers { rows.values.any { it.fundsState !in setOf(FundsState.AVAILABLE, FundsState.WITHDRAWN) } }
        every { fundsRepo.findTrackable() } answers { rows.values.filter { it.fundsState !in setOf(FundsState.AVAILABLE, FundsState.WITHDRAWN) } }
        every { fundsRepo.findTrackableByAssociationId(any()) } answers {
            rows.values.filter { it.donation.campaign.association.id == firstArg<UUID>() && it.fundsState !in setOf(FundsState.AVAILABLE, FundsState.WITHDRAWN) }
        }
        every { connectionRepo.findByAssociationId(any()) } answers { connection(HelloAssoConnectionState.ACTIVE) }
        every { tokenManager.getValidAccessToken(any()) } returns "access"
    }

    private fun at(instant: String) {
        executor.clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
    }

    private fun connection(state: HelloAssoConnectionState) =
        HelloAssoConnection(association = mockk(relaxed = true), webhookToken = "t", state = state, organizationSlug = "slug")

    private fun row(
        association: UUID,
        paymentId: Long,
        state: FundsState = FundsState.AT_PROVIDER,
        cashOutDate: Instant? = null,
        amount: String = "10.00",
    ): HelloAssoDonationFunds {
        val assoc = mockk<AssociationProfile> { every { id } returns association }
        val campaign = mockk<Campaign> { every { this@mockk.association } returns assoc }
        val donationId = UUID.randomUUID()
        val donation = mockk<Donation>(relaxed = true) {
            every { id } returns donationId
            every { this@mockk.campaign } returns campaign
            every { providerRef } returns "helloasso:$paymentId"
            every { this@mockk.amount } returns BigDecimal(amount)
        }
        return HelloAssoDonationFunds(donation = donation, haPaymentId = paymentId, cashOutState = "Transfered", fundsState = state, cashOutDate = cashOutDate)
            .also { it.donationId = donationId; rows[donationId] = it }
    }

    private fun payment(id: Long, cashOutState: String, state: String = "Authorized", cashOutDate: String? = null, idCashOut: Long? = null) =
        every { gateway.getPayment("access", id) } returns HelloAssoPaymentDetailJson(id, 1000, state, cashOutState, cashOutDate, idCashOut)

    @Test
    fun `nothing to track makes no call at all`() {
        executor.syncAll()

        verify(exactly = 0) { fundsRepo.findTrackable() }
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
        verify(exactly = 0) { gateway.getPayment(any(), any()) }
    }

    @Test
    fun `one access token per association and per pass`() {
        at("2026-11-10T08:00:00Z")
        row(assocA, 1); row(assocA, 2); row(assocB, 3)
        payment(1, "Transfered"); payment(2, "Transfered"); payment(3, "Transfered")

        executor.syncAll()

        verify(exactly = 1) { tokenManager.getValidAccessToken(assocA) }
        verify(exactly = 1) { tokenManager.getValidAccessToken(assocB) }
        verify(exactly = 3) { gateway.getPayment("access", any()) }
        assertNull(syncs[assocA]!!.syncError)
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), syncs[assocA]!!.lastSyncAt)
    }

    @Test
    fun `payout states move the donation forward and record the payout`() {
        at("2026-11-12T08:00:00Z")
        val waiting = row(assocA, 1)
        val paidOut = row(assocA, 2)
        payment(1, "WaitingForCashOutConfirmation")
        payment(2, "CashedOut", cashOutDate = "2026-11-12T10:00:00+01:00", idCashOut = 60001)

        executor.syncAll()

        assertEquals(FundsState.PAYOUT_PREPARING, waiting.fundsState)
        assertEquals(FundsState.IN_TRANSIT, paidOut.fundsState)
        assertEquals(Instant.parse("2026-11-12T09:00:00Z"), paidOut.cashOutDate)
        assertEquals(60001L, paidOut.idCashOut)
        assertEquals(Instant.parse("2026-11-12T08:00:00Z"), paidOut.cashedOutSeenAt)
        assertEquals(Instant.parse("2026-11-12T08:00:00Z"), paidOut.lastCheckedAt)
    }

    @Test
    fun `a 429 stops the pass of that association only`() {
        at("2026-11-10T08:00:00Z")
        val first = row(assocA, 1)
        val second = row(assocA, 2)
        val other = row(assocB, 3)
        every { gateway.getPayment("access", 1) } throws HelloAssoRateLimitedException("GET /payments")
        payment(2, "CashedOut"); payment(3, "WaitingForCashOutConfirmation")

        executor.syncAll()

        verify(exactly = 0) { gateway.getPayment("access", 2) }
        assertEquals(FundsState.AT_PROVIDER, first.fundsState)
        assertEquals(FundsState.AT_PROVIDER, second.fundsState)
        assertEquals(FundsState.PAYOUT_PREPARING, other.fundsState)
        assertEquals(HelloAssoCashOutSyncExecutor.ERROR_RATE_LIMITED, syncs[assocA]!!.syncError)
        assertEquals(1, syncs[assocA]!!.consecutiveFailures)
    }

    @Test
    fun `an HTTP error makes no transition and the pass goes on`() {
        at("2026-11-10T08:00:00Z")
        val failing = row(assocA, 1)
        val next = row(assocA, 2)
        every { gateway.getPayment("access", 1) } throws HelloAssoUnavailableException("GET /payments", 502)
        payment(2, "CashedOut")

        executor.syncAll()

        assertEquals(FundsState.AT_PROVIDER, failing.fundsState)
        assertNull(failing.lastCheckedAt)
        assertEquals(FundsState.IN_TRANSIT, next.fundsState)
        assertEquals(HelloAssoCashOutSyncExecutor.ERROR_UNAVAILABLE, syncs[assocA]!!.syncError)
    }

    @Test
    fun `a paid-out donation becomes available after the settlement delay, holidays skipped`() {
        // Paid out Thu 24/12/2026: 25 (holiday), 26-27 (weekend) are skipped; + 2 business days = Tue 29/12.
        val r = row(assocA, 1, state = FundsState.IN_TRANSIT, cashOutDate = Instant.parse("2026-12-24T09:00:00Z"))
        payment(1, "CashedOut")

        at("2026-12-28T08:00:00Z")
        executor.syncAll()
        assertEquals(FundsState.IN_TRANSIT, r.fundsState)

        at("2026-12-29T05:00:00Z")
        executor.syncAll()
        assertEquals(FundsState.AVAILABLE, r.fundsState)
        assertEquals(ReceiptConfirmationMode.AUTO, r.receiptConfirmationMode)
        assertNull(r.receiptConfirmedBy)
        verify { r.donation.fundsAvailableAt = Instant.parse("2026-12-29T05:00:00Z") }
    }

    @Test
    fun `availability is never back-dated when the pass runs late`() {
        val r = row(assocA, 1, state = FundsState.IN_TRANSIT, cashOutDate = Instant.parse("2026-11-12T09:00:00Z"))
        payment(1, "CashedOut")

        at("2026-12-01T12:00:00Z")
        executor.syncAll()

        assertEquals(FundsState.AVAILABLE, r.fundsState)
        assertEquals(Instant.parse("2026-12-01T12:00:00Z"), r.receiptConfirmedAt)
        verify { r.donation.fundsAvailableAt = Instant.parse("2026-12-01T12:00:00Z") }
    }

    @Test
    fun `without a payout date the delay starts when CashedOut was first seen`() {
        val r = row(assocA, 1)
        payment(1, "CashedOut")

        at("2026-11-13T08:00:00Z") // Friday
        executor.syncAll()
        assertEquals(FundsState.IN_TRANSIT, r.fundsState)

        at("2026-11-16T08:00:00Z") // Monday: one business day
        executor.syncAll()
        assertEquals(FundsState.IN_TRANSIT, r.fundsState)

        at("2026-11-17T08:00:00Z") // Tuesday: two
        executor.syncAll()
        assertEquals(FundsState.AVAILABLE, r.fundsState)
    }

    @Test
    fun `settlement still applies when the link is no longer active, without any HelloAsso call`() {
        every { connectionRepo.findByAssociationId(assocA) } returns connection(HelloAssoConnectionState.DISCONNECTED)
        val r = row(assocA, 1, state = FundsState.IN_TRANSIT, cashOutDate = Instant.parse("2026-11-12T09:00:00Z"))

        at("2026-11-20T08:00:00Z")
        executor.syncAll()

        assertEquals(FundsState.AVAILABLE, r.fundsState)
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
        verify(exactly = 0) { gateway.getPayment(any(), any()) }
        assertEquals(HelloAssoCashOutSyncExecutor.ERROR_NOT_LINKED, syncs[assocA]!!.syncError)
    }

    @Test
    fun `a refund read at HelloAsso withdraws the donation with a single alert`() {
        at("2026-11-12T08:00:00Z")
        val r = row(assocA, 1, state = FundsState.IN_TRANSIT, cashOutDate = Instant.parse("2026-11-12T09:00:00Z"))
        payment(1, "Refunded", state = "Refunded")

        executor.syncAll()
        executor.syncAll()

        assertEquals(FundsState.WITHDRAWN, r.fundsState)
        verify(exactly = 1) { alerts.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, "helloasso:1", null) }
        verify(exactly = 1) { auditLog.append(ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED, any(), any(), any(), any()) }
    }

    @Test
    fun `every pass stamps the refresh guard`() {
        at("2026-11-10T08:00:00Z")
        row(assocA, 1)
        payment(1, "Transfered")

        executor.syncAll()

        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), syncs[assocA]!!.lastRefreshRequestedAt)
    }

    // ── Alerts: once per event ─────────────────────────────────────────────

    @Test
    fun `a stuck payment is alerted once, not on every pass`() {
        at("2026-11-10T08:00:00Z")
        row(assocA, 1)
        payment(1, "CantTransferReceiverFull")

        executor.syncAll()
        executor.syncAll()

        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_RECEIVER_FULL, "helloasso:1", any()) }
    }

    @Test
    fun `an unknown state is alerted once it persists beyond the threshold, and only once`() {
        val r = row(assocA, 1)
        payment(1, "Unknown")

        at("2026-11-10T08:00:00Z")
        executor.syncAll()
        at("2026-11-12T08:00:00Z")
        executor.syncAll()
        verify(exactly = 0) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_STATE_UNKNOWN, any(), any()) }
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), r.unknownSince)

        at("2026-11-13T08:00:00Z")
        executor.syncAll()
        at("2026-11-14T08:00:00Z")
        executor.syncAll()
        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_STATE_UNKNOWN, "helloasso:1", "Unknown") }
        assertEquals(FundsState.AT_PROVIDER, r.fundsState)

        payment(1, "Transfered")
        executor.syncAll()
        assertNull(r.unknownSince)
        assertNull(r.unknownAlertedAt)
    }

    @Test
    fun `a donation paid out for too long without becoming available is alerted once`() {
        executor = executorWith(HelloAssoCashOutProperties(cashoutSettlementBusinessDays = 10, cashoutInTransitAlertBusinessDays = 5))
        row(assocA, 1, state = FundsState.IN_TRANSIT, cashOutDate = Instant.parse("2026-11-12T09:00:00Z"))
        payment(1, "CashedOut")

        at("2026-11-18T08:00:00Z") // 4 business days
        executor.syncAll()
        verify(exactly = 0) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_IN_TRANSIT_TOO_LONG, any(), any()) }

        at("2026-11-19T08:00:00Z") // 5
        executor.syncAll()
        at("2026-11-20T08:00:00Z")
        executor.syncAll()
        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_IN_TRANSIT_TOO_LONG, "helloasso:1", any()) }
    }

    @Test
    fun `repeated failed passes are alerted once per streak`() {
        at("2026-11-10T08:00:00Z")
        row(assocA, 1)
        every { gateway.getPayment("access", 1) } throws HelloAssoUnavailableException("GET /payments", 502)

        repeat(4) { executor.syncAll() }
        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_SYNC_FAILING, "association $assocA", "UNAVAILABLE") }
        assertEquals(4, syncs[assocA]!!.consecutiveFailures)

        payment(1, "Transfered")
        executor.syncAll()
        assertEquals(0, syncs[assocA]!!.consecutiveFailures)
        assertNull(syncs[assocA]!!.failureAlertedAt)
    }

    @Test
    fun `donations not paid out while the link is disconnected are alerted once with their count and amount`() {
        at("2026-11-10T08:00:00Z")
        every { connectionRepo.findByAssociationId(assocA) } returns connection(HelloAssoConnectionState.DISCONNECTED)
        row(assocA, 1, amount = "10.00")
        row(assocA, 2, state = FundsState.PAYOUT_PREPARING, amount = "15.50")
        row(assocA, 3, state = FundsState.IN_TRANSIT, cashOutDate = Instant.parse("2026-11-09T09:00:00Z"), amount = "99.00")

        executor.syncAll()
        executor.syncAll()

        verify(exactly = 1) {
            alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNLINKED, "association $assocA", "2 don(s), 25.50 EUR")
        }
        verify(exactly = 0) { gateway.getPayment(any(), any()) }
    }

    @Test
    fun `a broken link is alerted like a disconnected one, and the alert is re-armed once relinked`() {
        at("2026-11-10T08:00:00Z")
        every { connectionRepo.findByAssociationId(assocA) } returns connection(HelloAssoConnectionState.BROKEN)
        row(assocA, 1)
        executor.syncAll()
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), syncs[assocA]!!.unlinkedAlertedAt)

        every { connectionRepo.findByAssociationId(assocA) } returns connection(HelloAssoConnectionState.ACTIVE)
        payment(1, "Transfered")
        executor.syncAll()
        assertNull(syncs[assocA]!!.unlinkedAlertedAt)
    }

    @Test
    fun `two associations unlinked in the same pass are both alerted`() {
        at("2026-11-10T08:00:00Z")
        every { connectionRepo.findByAssociationId(any()) } returns connection(HelloAssoConnectionState.DISCONNECTED)
        row(assocA, 1)
        row(assocB, 2)

        executor.syncAll()

        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNLINKED, "association $assocA", any()) }
        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNLINKED, "association $assocB", any()) }
    }

    @Test
    fun `syncAssociation returns the stored result`() {
        at("2026-11-10T08:00:00Z")
        row(assocA, 1)
        every { gateway.getPayment("access", 1) } throws HelloAssoUnavailableException("GET /payments", 502)

        val result = executor.syncAssociation(assocA)

        assertEquals(CashOutSyncResult(Instant.parse("2026-11-10T08:00:00Z"), HelloAssoCashOutSyncExecutor.ERROR_UNAVAILABLE), result)
    }
}

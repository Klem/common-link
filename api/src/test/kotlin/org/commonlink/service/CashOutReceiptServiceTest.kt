package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.dto.HelloAssoPaymentDetailJson
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.Donation
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.CashOutNotCashedOutException
import org.commonlink.exception.HelloAssoNotLinkedException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** "Transfer received": re-read at HelloAsso, one transaction, one journal entry, idempotent, never on the click alone. */
class CashOutReceiptServiceTest {

    private val fundsRepo = mockk<HelloAssoDonationFundsRepository>()
    private val connectionRepo = mockk<HelloAssoConnectionRepository>()
    private val tokenManager = mockk<HelloAssoTokenManager>()
    private val breaker = mockk<HelloAssoConnectionBreaker>(relaxed = true)
    private val gateway = mockk<HelloAssoGateway>()
    private val alerts = mockk<TechnicalAlertService>(relaxed = true)
    private val auditLog = mockk<ComplianceAuditLogService>(relaxed = true)
    private val totals = mockk<HelloAssoCashOutTotalService>()
    private val fundsService = HelloAssoDonationFundsService(fundsRepo, alerts, auditLog)
    private val service = CashOutReceiptService(
        fundsRepo, connectionRepo, tokenManager, breaker, gateway, fundsService, totals, auditLog,
        TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
    ).also { it.clock = Clock.fixed(NOW, ZoneOffset.UTC) }

    private val association = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val rows = linkedMapOf<UUID, HelloAssoDonationFunds>()
    private val availableAt = mutableMapOf<UUID, Instant?>()

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T09:00:00Z")
        const val PAYOUT = 60001L
    }

    init {
        every { fundsRepo.findByAssociationIdAndIdCashOut(association, any()) } answers {
            rows.values.filter { it.idCashOut == secondArg<Long>() }
        }
        every { fundsRepo.lockAllForUpdate(any()) } answers { firstArg<Collection<UUID>>().sorted().mapNotNull { rows[it] } }
        every { fundsRepo.findByDonationIdForUpdate(any()) } answers { rows[firstArg()] }
        every { fundsRepo.save(any<HelloAssoDonationFunds>()) } answers { firstArg() }
        every { connectionRepo.findByAssociationId(association) } returns connection(HelloAssoConnectionState.ACTIVE)
        every { tokenManager.getValidAccessToken(association) } returns "access"
        every { totals.netTotalOf(association, any(), "access", "mon-asso") } returns BigDecimal("30.00")
    }

    private fun connection(state: HelloAssoConnectionState) =
        HelloAssoConnection(association = mockk(relaxed = true), webhookToken = "t", state = state, organizationSlug = "mon-asso")

    private fun row(paymentId: Long, state: FundsState = FundsState.IN_TRANSIT, amount: String = "10.00", idCashOut: Long = PAYOUT): HelloAssoDonationFunds {
        val assoc = mockk<AssociationProfile> { every { id } returns association }
        val campaign = mockk<Campaign> {
            every { id } returns campaignId
            every { this@mockk.association } returns assoc
        }
        val donationId = UUID.randomUUID()
        val donation = mockk<Donation> {
            every { id } returns donationId
            every { this@mockk.campaign } returns campaign
            every { providerRef } returns "helloasso:$paymentId"
            every { this@mockk.amount } returns BigDecimal(amount)
            every { fundsAvailableAt } answers { availableAt[donationId] }
            every { fundsAvailableAt = any() } answers { availableAt[donationId] = firstArg() }
        }
        return HelloAssoDonationFunds(donation = donation, haPaymentId = paymentId, cashOutState = "CashedOut", fundsState = state, idCashOut = idCashOut)
            .also { it.donationId = donationId; rows[donationId] = it }
    }

    private fun payment(id: Long, cashOutState: String = "CashedOut", idCashOut: Long? = PAYOUT, state: String = "Authorized") =
        every { gateway.getPayment("access", id) } returns HelloAssoPaymentDetailJson(id, 1000, state, cashOutState, "2026-11-10T10:00:00+01:00", idCashOut)

    @Test
    fun `every donation re-read as paid out in this payout becomes available now, journaled once`() {
        val a = row(70001, amount = "10.00")
        val b = row(70002, FundsState.PAYOUT_PREPARING, amount = "15.50")
        payment(70001)
        payment(70002)

        val result = service.confirm(association, PAYOUT, actor)

        for (r in listOf(a, b)) {
            assertEquals(FundsState.AVAILABLE, r.fundsState)
            assertEquals(ReceiptConfirmationMode.MANUAL, r.receiptConfirmationMode)
            assertEquals(actor, r.receiptConfirmedBy)
            assertEquals(NOW, r.receiptConfirmedAt)
            assertEquals(NOW, availableAt[r.donationId])
        }
        assertEquals(setOf(a.donationId, b.donationId), result.donations.map { it.donationId }.toSet())
        assertEquals(BigDecimal("30.00"), result.netTotal)
        val payload = slot<Map<String, Any?>>()
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.CASHOUT_RECEIPT_CONFIRMED, ComplianceAuditSubjectType.ASSOCIATION, capture(payload), association, actor)
        }
        assertEquals(mapOf("idCashOut" to PAYOUT, "donationCount" to 2, "amount" to "25.50"), payload.captured)
        verify(exactly = 1) { tokenManager.getValidAccessToken(association) }
    }

    @Test
    fun `one donation not paid out in this payout refuses the whole confirmation, nothing changes`() {
        val a = row(70001)
        val b = row(70002)
        payment(70001)
        payment(70002, cashOutState = "WaitingForCashOutConfirmation", idCashOut = null)

        assertThrows<CashOutNotCashedOutException> { service.confirm(association, PAYOUT, actor) }

        assertEquals(FundsState.IN_TRANSIT, a.fundsState)
        assertEquals(FundsState.IN_TRANSIT, b.fundsState)
        assertNull(availableAt[a.donationId])
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a donation paid out in another payout is refused too`() {
        row(70001)
        payment(70001, idCashOut = 99999)

        assertThrows<CashOutNotCashedOutException> { service.confirm(association, PAYOUT, actor) }
    }

    @Test
    fun `a payout without any donation of the association is not found, without calling HelloAsso`() {
        assertThrows<NotFoundException> { service.confirm(association, PAYOUT, actor) }
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
    }

    @Test
    fun `a second confirmation is a no-op without a second journal entry`() {
        row(70001)
        payment(70001)
        service.confirm(association, PAYOUT, actor)

        val again = service.confirm(association, PAYOUT, actor)

        assertEquals(1, again.donations.size)
        verify(exactly = 1) { auditLog.append(ComplianceAuditLogService.CASHOUT_RECEIPT_CONFIRMED, any(), any(), any(), any()) }
        verify(exactly = 1) { gateway.getPayment(any(), any()) }
    }

    @Test
    fun `donations already available by the settlement delay are kept and not re-read, the others are journaled alone`() {
        val auto = row(70001, FundsState.AVAILABLE, amount = "10.00").also { it.receiptConfirmationMode = ReceiptConfirmationMode.AUTO }
        val pending = row(70002, amount = "7.00")
        payment(70002)

        val result = service.confirm(association, PAYOUT, actor)

        assertEquals(setOf(auto.donationId, pending.donationId), result.donations.map { it.donationId }.toSet())
        assertEquals(ReceiptConfirmationMode.AUTO, auto.receiptConfirmationMode)
        verify(exactly = 0) { gateway.getPayment("access", 70001) }
        val payload = slot<Map<String, Any?>>()
        verify(exactly = 1) { auditLog.append(ComplianceAuditLogService.CASHOUT_RECEIPT_CONFIRMED, any(), capture(payload), any(), any()) }
        assertEquals(1, payload.captured["donationCount"])
        assertEquals("7.00", payload.captured["amount"])
    }

    @Test
    fun `a row made available by the tracking pass between re-read and transaction is not journaled again`() {
        val a = row(70001)
        every { gateway.getPayment("access", 70001) } answers {
            a.fundsState = FundsState.AVAILABLE // settlement delay elapsed meanwhile
            a.receiptConfirmationMode = ReceiptConfirmationMode.AUTO
            HelloAssoPaymentDetailJson(70001, 1000, "Authorized", "CashedOut", null, PAYOUT)
        }

        service.confirm(association, PAYOUT, actor)

        assertEquals(ReceiptConfirmationMode.AUTO, a.receiptConfirmationMode)
        verify(exactly = 0) { auditLog.append(ComplianceAuditLogService.CASHOUT_RECEIPT_CONFIRMED, any(), any(), any(), any()) }
    }

    @Test
    fun `withdrawn donations are left out, and a refund discovered at re-read is withdrawn once while the others are confirmed`() {
        val withdrawn = row(70001, FundsState.WITHDRAWN)
        val refunded = row(70002)
        val ok = row(70003)
        payment(70002, cashOutState = "Refunded", state = "Refunded")
        payment(70003)

        val result = service.confirm(association, PAYOUT, actor)

        assertEquals(listOf(ok.donationId), result.donations.map { it.donationId })
        assertEquals(FundsState.WITHDRAWN, withdrawn.fundsState)
        assertEquals(FundsState.WITHDRAWN, refunded.fundsState)
        verify(exactly = 0) { gateway.getPayment("access", 70001) }
        verify(exactly = 1) { auditLog.append(ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED, any(), any(), refunded.donationId, any()) }
        verify(exactly = 1) { alerts.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, "helloasso:70002", null) }
    }

    @Test
    fun `without an active link the confirmation is refused as not linked, without any call`() {
        row(70001)
        every { connectionRepo.findByAssociationId(association) } returns connection(HelloAssoConnectionState.DISCONNECTED)

        assertThrows<HelloAssoNotLinkedException> { service.confirm(association, PAYOUT, actor) }
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
    }

    @Test
    fun `a rejected token breaks the link and is refused as not linked`() {
        val a = row(70001)
        every { tokenManager.getValidAccessToken(association) } throws HelloAssoUnauthorizedException("refresh", 401)

        assertThrows<HelloAssoNotLinkedException> { service.confirm(association, PAYOUT, actor) }
        verify { breaker.markBroken(association) }
        assertEquals(FundsState.IN_TRANSIT, a.fundsState)
    }

    @Test
    fun `a payment read rejected with 401 is a 502 and does not break the link, like the tracking pass`() {
        val a = row(70001)
        every { gateway.getPayment("access", 70001) } throws HelloAssoUnauthorizedException("GET /payments", 401)

        assertThrows<BadGatewayException> { service.confirm(association, PAYOUT, actor) }
        verify(exactly = 0) { breaker.markBroken(any()) }
        assertEquals(FundsState.IN_TRANSIT, a.fundsState)
    }

    @Test
    fun `HelloAsso unavailable or rate limiting at re-read is a 502, nothing changes`() {
        val a = row(70001)
        val b = row(70002)
        payment(70001)
        every { gateway.getPayment("access", 70002) } throws HelloAssoUnavailableException("GET", 503) andThenThrows HelloAssoRateLimitedException("GET")

        assertThrows<BadGatewayException> { service.confirm(association, PAYOUT, actor) }
        assertThrows<BadGatewayException> { service.confirm(association, PAYOUT, actor) }

        assertEquals(FundsState.IN_TRANSIT, a.fundsState)
        assertEquals(FundsState.IN_TRANSIT, b.fundsState)
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
    }
}

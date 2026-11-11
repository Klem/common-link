package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.HelloAssoCashOutProperties
import org.commonlink.config.PaymentsProperties
import org.commonlink.dto.CampaignFundsResponse
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoCashOutSync
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.NotFoundException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoCashOutSyncRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/** Funds read model: segments add up, `disbursable` is the creation check's function, payouts grouped, edge cases kept. */
class CampaignFundsServiceTest {

    private val campaignRepo = mockk<CampaignRepository>()
    private val associationRepo = mockk<AssociationProfileRepository>()
    private val donationRepo = mockk<DonationRepository>()
    private val payoutRepo = mockk<PayoutRepository>()
    private val fundsRepo = mockk<HelloAssoDonationFundsRepository>()
    private val syncRepo = mockk<HelloAssoCashOutSyncRepository>()
    private val payoutService = mockk<PayoutService>()
    private val totals = mockk<HelloAssoCashOutTotalService>()
    private val refreshService = mockk<HelloAssoCashOutRefreshService>()
    private val service = CampaignFundsService(
        campaignRepo, associationRepo, donationRepo, payoutRepo, fundsRepo, syncRepo, payoutService, totals, refreshService,
        PaymentsProperties(helloasso = HelloAssoCashOutProperties()),
    ).also { it.clock = Clock.fixed(NOW, ZoneOffset.UTC) }

    private val userId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val association = mockk<AssociationProfile>(relaxed = true)
    private val rows = mutableListOf<HelloAssoDonationFunds>()
    private var sync: HelloAssoCashOutSync? = null

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T09:00:00Z")
    }

    init {
        every { association.id } returns associationId
        every { association.paymentProvider } returns PaymentProviderId.HELLOASSO
        every { associationRepo.findByUserId(userId) } returns Optional.of(association)
        every { associationRepo.findById(associationId) } returns Optional.of(association)
        val owner = association
        val campaign = mockk<Campaign> { every { this@mockk.association } returns owner }
        every { campaignRepo.findById(campaignId) } returns Optional.of(campaign)
        every { fundsRepo.findByCampaignId(campaignId) } answers { rows.toList() }
        every { fundsRepo.existsByAssociationId(associationId) } answers { rows.isNotEmpty() }
        every { syncRepo.findById(associationId) } answers { Optional.ofNullable(sync) }
        every { payoutRepo.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED) } returns BigDecimal("100.00")
        every { payoutRepo.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.PENDING) } returns BigDecimal("50.00")
        every { totals.netTotalOf(associationId, any()) } returns null
        balances(raised = "0", available = "0", disbursable = "0")
    }

    private fun balances(raised: String, available: String, disbursable: String) {
        every { donationRepo.sumConfirmedAmountByCampaignId(campaignId) } returns BigDecimal(raised)
        every { donationRepo.sumAvailableAmountByCampaignId(campaignId) } returns BigDecimal(available)
        every { payoutService.computeAvailableBalance(campaignId) } returns BigDecimal(disbursable)
    }

    private fun row(
        state: FundsState,
        amount: String,
        idCashOut: Long? = null,
        cashOutDate: Instant? = null,
        mode: ReceiptConfirmationMode? = null,
        confirmedAt: Instant? = null,
    ): HelloAssoDonationFunds {
        val donation = mockk<Donation> {
            every { this@mockk.amount } returns BigDecimal(amount)
            every { this@mockk.confirmedAt } returns Instant.parse("2026-11-01T10:00:00Z")
        }
        return HelloAssoDonationFunds(donation = donation, haPaymentId = 1, fundsState = state, idCashOut = idCashOut, cashOutDate = cashOutDate)
            .also {
                it.donationId = UUID.randomUUID()
                it.receiptConfirmationMode = mode
                it.receiptConfirmedAt = confirmedAt
                rows += it
            }
    }

    private fun CampaignFundsResponse.segments() = atProvider + payoutPreparing + inTransit + available

    @Test
    fun `a Mollie association has every donation available and no payout`() {
        every { association.paymentProvider } returns PaymentProviderId.MOLLIE
        balances(raised = "420.00", available = "420.00", disbursable = "270.00")

        val funds = service.getFunds(campaignId, userId)

        assertEquals(PaymentProviderId.MOLLIE, funds.provider)
        assertEquals(0, BigDecimal("420.00").compareTo(funds.available))
        assertEquals(0, BigDecimal.ZERO.compareTo(funds.atProvider))
        assertEquals(emptyList<Any>(), funds.cashOuts)
        assertEquals(0, BigDecimal("270.00").compareTo(funds.disbursable))
        assertEquals(0, BigDecimal("100.00").compareTo(funds.paidOut))
        assertEquals(0, BigDecimal("50.00").compareTo(funds.pendingPayouts))
        assertNull(funds.nextRefreshAvailableAt)
        assertFalse(funds.syncError)
    }

    @Test
    fun `HelloAsso donations in every state add up to raised minus withdrawn, disbursable is the creation check's`() {
        row(FundsState.AT_PROVIDER, "40.00")
        row(FundsState.PAYOUT_PREPARING, "25.00")
        row(FundsState.IN_TRANSIT, "30.00", idCashOut = 60002, cashOutDate = Instant.parse("2026-11-10T10:00:00Z"))
        row(FundsState.AVAILABLE, "60.00", idCashOut = 60001, cashOutDate = Instant.parse("2026-10-09T10:00:00Z"),
            mode = ReceiptConfirmationMode.MANUAL, confirmedAt = Instant.parse("2026-10-12T09:00:00Z"))
        row(FundsState.WITHDRAWN, "9.00", idCashOut = 60001)
        // 10.00 of Mollie donations from before the provider change, available too.
        balances(raised = "174.00", available = "70.00", disbursable = "-5.00")

        val funds = service.getFunds(campaignId, userId)

        assertEquals(0, BigDecimal("40.00").compareTo(funds.atProvider))
        assertEquals(0, BigDecimal("25.00").compareTo(funds.payoutPreparing))
        assertEquals(0, BigDecimal("30.00").compareTo(funds.inTransit))
        assertEquals(0, BigDecimal("70.00").compareTo(funds.available))
        assertEquals(0, BigDecimal("9.00").compareTo(funds.withdrawn))
        assertEquals(0, (funds.raised - funds.withdrawn).compareTo(funds.segments()))
        assertEquals(0, BigDecimal.ZERO.compareTo(funds.disbursable), "clamped at zero")
        verify { payoutService.computeAvailableBalance(campaignId) }

        assertEquals(listOf(60002L, 60001L), funds.cashOuts.map { it.idCashOut })
        val inTransit = funds.cashOuts[0]
        assertEquals(FundsState.IN_TRANSIT, inTransit.state)
        assertEquals(LocalDate.parse("2026-11-10"), inTransit.cashOutDate)
        assertEquals(LocalDate.parse("2026-11-12"), inTransit.autoAvailableOn)
        assertNull(inTransit.confirmedBy)
        val arrived = funds.cashOuts[1]
        assertEquals(FundsState.AVAILABLE, arrived.state)
        assertEquals(0, BigDecimal("60.00").compareTo(arrived.campaignAmount), "the withdrawn donation is excluded")
        assertEquals(ReceiptConfirmationMode.MANUAL, arrived.confirmedBy)
        assertEquals(Instant.parse("2026-10-12T09:00:00Z"), arrived.confirmedAt)
    }

    @Test
    fun `a curator release without payout is available, listed apart, outside cashOuts`() {
        val at = Instant.parse("2026-11-11T15:00:00Z")
        row(FundsState.AVAILABLE, "12.00", mode = ReceiptConfirmationMode.CURATOR_RELEASE, confirmedAt = at)
        row(FundsState.AVAILABLE, "8.00", mode = ReceiptConfirmationMode.CURATOR_RELEASE, confirmedAt = at)
        balances(raised = "20.00", available = "20.00", disbursable = "20.00")

        val funds = service.getFunds(campaignId, userId)

        assertEquals(emptyList<Any>(), funds.cashOuts)
        assertEquals(1, funds.manualReleases.size)
        assertEquals(at, funds.manualReleases[0].releasedAt)
        assertEquals(0, BigDecimal("20.00").compareTo(funds.manualReleases[0].campaignAmount))
        assertEquals(0, BigDecimal("20.00").compareTo(funds.available))
        assertEquals(0, BigDecimal.ZERO.compareTo(funds.atProvider))
    }

    @Test
    fun `a HelloAsso donation without tracking row is never lost`() {
        // Confirmed before the tracking (available at once) — 15.00; confirmed without trackable payment — 5.00.
        row(FundsState.AT_PROVIDER, "10.00")
        balances(raised = "30.00", available = "15.00", disbursable = "15.00")

        val funds = service.getFunds(campaignId, userId)

        assertEquals(0, BigDecimal("15.00").compareTo(funds.available))
        assertEquals(0, BigDecimal("15.00").compareTo(funds.atProvider))
        assertEquals(0, funds.raised.compareTo(funds.segments()))
    }

    @Test
    fun `a campaign without donation is all zeros`() {
        val funds = service.getFunds(campaignId, userId)

        assertEquals(0, BigDecimal.ZERO.compareTo(funds.raised))
        assertEquals(0, BigDecimal.ZERO.compareTo(funds.segments()))
        assertEquals(emptyList<Any>(), funds.cashOuts)
        assertEquals(emptyList<Any>(), funds.manualReleases)
    }

    @Test
    fun `the last pass and the next possible refresh are reported`() {
        row(FundsState.AT_PROVIDER, "10.00")
        sync = HelloAssoCashOutSync(associationId, lastSyncAt = NOW.minusSeconds(300), syncError = "UNAVAILABLE",
            lastRefreshRequestedAt = NOW.minusSeconds(300))

        val funds = service.getFunds(campaignId, userId)

        assertEquals(NOW.minusSeconds(300), funds.lastSyncAt)
        assertTrue(funds.syncError)
        assertEquals("UNAVAILABLE", funds.syncErrorCode)
        assertEquals(NOW.plusSeconds(600), funds.nextRefreshAvailableAt)

        sync!!.lastRefreshRequestedAt = NOW.minusSeconds(3600)
        assertNull(service.getFunds(campaignId, userId).nextRefreshAvailableAt)
    }

    @Test
    fun `refresh asks for a pass only for an association with HelloAsso donations`() {
        balances(raised = "0", available = "0", disbursable = "0")
        service.refresh(campaignId, userId)
        verify(exactly = 0) { refreshService.requestRefresh(any()) }

        row(FundsState.AT_PROVIDER, "10.00")
        every { refreshService.requestRefresh(associationId) } returns
            CashOutRefreshOutcome(CashOutSyncResult(NOW, null), true, NOW.plusSeconds(900))
        val funds = service.refresh(campaignId, userId)

        verify(exactly = 1) { refreshService.requestRefresh(associationId) }
        assertEquals(NOW.plusSeconds(900), funds.nextRefreshAvailableAt)
    }

    @Test
    fun `another association's campaign is not found`() {
        val other = mockk<AssociationProfile> { every { id } returns UUID.randomUUID() }
        every { campaignRepo.findById(campaignId) } returns Optional.of(mockk { every { association } returns other })

        assertThrows<NotFoundException> { service.getFunds(campaignId, userId) }
        assertThrows<NotFoundException> { service.refresh(campaignId, userId) }
    }
}

package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.DonationReceipt
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.repository.DonationReceiptRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The reversal alert of [HelloAssoDonationFundsService.recordReversal] is what the CommonLink team works from to handle a
 * HelloAsso refund by hand: one alert per donation (never swallowed by another donation's cooldown), carrying the
 * donation, amount, date, association, campaign, tax receipt and HelloAsso state — and no donor data.
 */
class HelloAssoDonationFundsServiceTest {

    private val rows = mutableMapOf<UUID, HelloAssoDonationFunds>()
    private val fundsRepo = mockk<HelloAssoDonationFundsRepository> {
        every { findByDonationIdForUpdate(any()) } answers { rows[firstArg()] }
        every { save(any()) } answers { firstArg() }
    }
    private val receipts = mutableMapOf<UUID, String>()
    private val receiptRepo = mockk<DonationReceiptRepository> {
        every { findByDonationId(any()) } answers {
            receipts[firstArg()]?.let { number -> mockk<DonationReceipt> { every { receiptNumber } returns number } }
        }
    }
    private val alerts = mockk<TechnicalAlertService>(relaxed = true)
    private val service = HelloAssoDonationFundsService(fundsRepo, alerts, mockk(relaxed = true), receiptRepo)

    private fun donation(ref: String, amount: String, receipt: String?): Donation {
        val assoc = mockk<AssociationProfile> { every { name } returns "Le Sens du Test" }
        val campaign = mockk<Campaign> {
            every { name } returns "Forêt 2026"
            every { association } returns assoc
        }
        val id = UUID.randomUUID()
        if (receipt != null) receipts[id] = receipt
        val donation = mockk<Donation> {
            every { this@mockk.id } returns id
            every { providerRef } returns ref
            every { this@mockk.amount } returns BigDecimal(amount)
            every { this@mockk.campaign } returns campaign
            every { confirmedAt } returns Instant.parse("2026-11-02T23:30:00Z")
            every { createdAt } returns Instant.parse("2026-11-02T23:00:00Z")
            every { donorFullName } throws AssertionError("the reversal alert must not read donor data")
        }
        rows[id] = HelloAssoDonationFunds(donation = donation, haPaymentId = 1, cashOutState = "CashedOut", fundsState = FundsState.AVAILABLE)
        return donation
    }

    @Test
    fun `two donations reversed in a row each raise their own alert`() {
        service.recordReversal(donation("helloasso:1", "40", "2026-0007"), "Refunded", null)
        service.recordReversal(donation("helloasso:2", "15.5", null), null, "Contested")

        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.PAYMENT_REVERSED, "helloasso:1", any()) }
        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.PAYMENT_REVERSED, "helloasso:2", any()) }
        verify(exactly = 0) { alerts.reportFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `the alert carries what the team needs to handle the refund by hand`() {
        val donation = donation("helloasso:1", "40", "2026-0007")
        val detail = slot<String>()
        every { alerts.reportForSubject(TechnicalAlertKind.PAYMENT_REVERSED, "helloasso:1", capture(detail)) } returns Unit

        service.recordReversal(donation, "Refunded", null)

        listOf(
            "Don : ${donation.id}",
            "Montant : 40,00 €",
            "Date du don : 2026-11-03", // confirmed at 23:30 UTC = 00:30 in Paris
            "Association : Le Sens du Test",
            "Campagne : Forêt 2026",
            "Reçu fiscal : 2026-0007",
            "État HelloAsso : Refunded",
            "Fonds avant : AVAILABLE",
        ).forEach { assertTrue(detail.captured.contains(it)) { "missing <$it> in <${detail.captured}>" } }
    }

    @Test
    fun `an alert detail that cannot be built never blocks the withdrawal nor the alert`() {
        val donation = donation("helloasso:3", "10", null)
        val donationId = donation.id!! // read outside every {}: a mock call inside it is recorded, not answered
        every { receiptRepo.findByDonationId(donationId) } throws IllegalStateException("db down")

        service.recordReversal(donation, "Refunded", null)

        assertEquals(FundsState.WITHDRAWN, rows[donationId]!!.fundsState)
        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.PAYMENT_REVERSED, "helloasso:3", null) }
    }

    @Test
    fun `a contested donation without receipt says so`() {
        val detail = slot<String>()
        every { alerts.reportForSubject(TechnicalAlertKind.PAYMENT_REVERSED, "helloasso:2", capture(detail)) } returns Unit

        service.recordReversal(donation("helloasso:2", "15.5", null), null, "Contested")

        assertTrue(detail.captured.contains("Montant : 15,50 €")) { detail.captured }
        assertTrue(detail.captured.contains("Reçu fiscal : aucun")) { detail.captured }
        assertTrue(detail.captured.contains("État HelloAsso : Contested")) { detail.captured }
    }
}

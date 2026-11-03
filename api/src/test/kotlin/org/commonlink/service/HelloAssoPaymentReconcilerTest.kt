package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.HelloAssoProperties
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Re-reads stale `helloasso:` donations within the 45-minute window; a late confirmation flags a missing webhook. */
class HelloAssoPaymentReconcilerTest {

    private val donationRepo = mockk<DonationRepository>()
    private val connectionRepo = mockk<HelloAssoConnectionRepository>()
    private val checkoutService = mockk<HelloAssoCheckoutService>()
    private val alerts = mockk<TechnicalAlertService>(relaxed = true)
    private val reconciler = HelloAssoPaymentReconciler(donationRepo, checkoutService, alerts, HelloAssoProperties())

    private val associationId = UUID.randomUUID()
    private val row = HelloAssoConnection(association = mockk<AssociationProfile>(relaxed = true), webhookToken = "t")

    private fun donation(ref: String, minutesAgo: Long) = mockk<Donation> {
        every { id } returns UUID.randomUUID()
        every { providerRef } returns ref
        every { createdAt } returns Instant.now().minusSeconds(minutesAgo * 60)
        every { campaign } returns mockk<Campaign> { every { association } returns mockk { every { id } returns associationId } }
    }

    @Test
    fun `nothing stale - exits without any HelloAsso call`() {
        every { donationRepo.findStalePendingByProviderRefPrefix("helloasso:", any()) } returns emptyList()

        reconciler.reconcile()

        verify(exactly = 0) { checkoutService.refreshPayment(any()) }
    }

    @Test
    fun `a donation confirmed by the reconciler raises MISSED_WEBHOOK and flags the link`() {
        every { donationRepo.findStalePendingByProviderRefPrefix("helloasso:", any()) } returns listOf(donation("helloasso:1", 20))
        every { checkoutService.refreshPayment("helloasso:1") } returns HelloAssoPaymentOutcome.CONFIRMED
        every { checkoutService.markWebhookMissing("helloasso:1") } returns Unit

        reconciler.reconcile()

        verify { alerts.reportFailure(TechnicalAlertKind.MISSED_WEBHOOK, null, "helloasso:1", null) }
        verify { checkoutService.markWebhookMissing("helloasso:1") }
    }

    @Test
    fun `a still pending donation raises nothing, an abandoned one (over 45 minutes) is not re-read`() {
        every { donationRepo.findStalePendingByProviderRefPrefix("helloasso:", any()) } returns
            listOf(donation("helloasso:2", 20), donation("helloasso:3", 60))
        every { checkoutService.refreshPayment("helloasso:2") } returns HelloAssoPaymentOutcome.PENDING

        reconciler.reconcile()

        verify(exactly = 0) { checkoutService.refreshPayment("helloasso:3") }
        verify(exactly = 0) { alerts.reportFailure(any(), any(), any(), any()) }
        verify(exactly = 0) { checkoutService.markWebhookMissing(any()) }
    }

    @Test
    fun `a failed re-read is skipped without stopping the others`() {
        every { donationRepo.findStalePendingByProviderRefPrefix("helloasso:", any()) } returns
            listOf(donation("helloasso:4", 20), donation("helloasso:5", 20))
        every { checkoutService.refreshPayment("helloasso:4") } throws IllegalStateException("down")
        every { checkoutService.refreshPayment("helloasso:5") } returns HelloAssoPaymentOutcome.PENDING

        reconciler.reconcile()

        verify { checkoutService.refreshPayment("helloasso:5") }
    }
}

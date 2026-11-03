package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.config.MollieProperties
import org.commonlink.dto.HelloAssoCheckoutIntentJson
import org.commonlink.dto.HelloAssoCheckoutIntentResponseJson
import org.commonlink.dto.HelloAssoInitCheckoutJson
import org.commonlink.dto.HelloAssoOrderJson
import org.commonlink.dto.HelloAssoPaymentJson
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.ConflictException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.payment.CheckoutPayer
import org.commonlink.payment.CheckoutRequest
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.UUID

/** Checkout body (flow §4), 409 handling, and the payment states applied on re-read (flow §6). */
class HelloAssoCheckoutServiceTest {

    private val connectionRepo = mockk<HelloAssoConnectionRepository>()
    private val donationRepo = mockk<DonationRepository>(relaxed = true)
    private val donationService = mockk<DonationService>(relaxed = true)
    private val tokenManager = mockk<HelloAssoTokenManager>()
    private val gateway = mockk<HelloAssoGateway>()
    private val breaker = mockk<HelloAssoConnectionBreaker>(relaxed = true)
    private val eligibility = mockk<HelloAssoEligibilityService>(relaxed = true)
    private val alerts = mockk<TechnicalAlertService>(relaxed = true)
    private val auditLog = mockk<ComplianceAuditLogService>(relaxed = true)
    private val service = HelloAssoCheckoutService(
        connectionRepo, donationRepo, donationService, tokenManager, gateway, breaker, eligibility, alerts, auditLog,
        MollieProperties(redirectBaseUrl = "https://app.common-link.test"),
    )

    private val associationId = UUID.randomUUID()
    private val publicRef = UUID.randomUUID()
    private val row = HelloAssoConnection(
        association = mockk<AssociationProfile>(relaxed = true), webhookToken = "t",
        state = HelloAssoConnectionState.ACTIVE, organizationSlug = "techno-plus", canCollect = true,
    )

    private fun request(locale: String = "fr") = CheckoutRequest(
        associationId = associationId, campaignId = UUID.randomUUID(), campaignName = "Classe verte",
        donorProfileId = UUID.randomUUID(), amount = BigDecimal("25.50"), publicRef = publicRef, widgetToken = "clk_w",
        locale = locale, sourceSite = "https://asso.org/page?a=1",
        payer = CheckoutPayer("jean@example.org", "Jean Dupont", "1 rue", "75001", "Paris", "FR"),
    )

    @BeforeEach
    fun setUp() {
        every { connectionRepo.findByAssociationId(associationId) } returns row
        every { tokenManager.getValidAccessToken(associationId) } returns "access"
        every { donationRepo.save(any<Donation>()) } answers { firstArg() }
    }

    @Test
    fun `the checkout body follows flow section 4, and the reference is prefixed`() {
        val body = slot<HelloAssoInitCheckoutJson>()
        every { gateway.createCheckoutIntent("access", "techno-plus", capture(body)) } returns
            HelloAssoCheckoutIntentResponseJson(4242, "https://checkout.helloasso.com/pay/4242")

        val session = service.createCheckout(request())

        assertEquals("helloasso:4242", session.providerRef)
        assertEquals("https://checkout.helloasso.com/pay/4242", session.redirectUrl)
        val b = body.captured
        assertEquals(2550, b.totalAmount)
        assertEquals(2550, b.initialAmount)
        assertEquals("Don - Classe verte", b.itemName)
        assertTrue(b.containsDonation)
        val base = "https://app.common-link.test/fr/embed/donate/clk_w/return"
        val source = "&source=https%3A%2F%2Fasso.org%2Fpage%3Fa%3D1"
        assertEquals("$base?ref=$publicRef$source", b.returnUrl)
        assertEquals("$base?cancelled=true$source", b.backUrl)
        assertEquals("$base?cancelled=true&type=error$source", b.errorUrl)
        assertEquals(publicRef.toString(), b.metadata!!["publicRef"])
        assertEquals("FRA", b.payer!!.country)
        assertEquals("Jean", b.payer!!.firstName)
    }

    @Test
    fun `an English donor gets lang=en on the HelloAsso page`() {
        every { gateway.createCheckoutIntent(any(), any(), any()) } returns
            HelloAssoCheckoutIntentResponseJson(1, "https://checkout.helloasso.com/pay/1?x=y")

        assertEquals("https://checkout.helloasso.com/pay/1?x=y&lang=en", service.createCheckout(request("en")).redirectUrl)
    }

    @Test
    fun `an association that cannot collect is refused before any HelloAsso call`() {
        row.canCollect = false

        assertThrows<ConflictException> { service.createCheckout(request()) }
        verify(exactly = 0) { gateway.createCheckoutIntent(any(), any(), any()) }
    }

    @Test
    fun `a HelloAsso 409 records the lost eligibility, re-reads it and refuses`() {
        every { gateway.createCheckoutIntent(any(), any(), any()) } throws HelloAssoNotEligibleException("POST")

        assertThrows<ConflictException> { service.createCheckout(request()) }
        verify { eligibility.store(associationId, false) }
        verify { eligibility.refresh(associationId) }
    }

    @Test
    fun `a rejected token breaks the link and refuses`() {
        every { tokenManager.getValidAccessToken(associationId) } throws HelloAssoUnauthorizedException("refresh_token", 400)

        assertThrows<ConflictException> { service.createCheckout(request()) }
        verify { breaker.markBroken(associationId) }
    }

    // ── Re-read ────────────────────────────────────────────────────────────

    private fun donation(confirmed: Boolean = false): Donation {
        val association = mockk<AssociationProfile> { every { id } returns associationId }
        val campaign = mockk<Campaign> {
            every { id } returns UUID.randomUUID()
            every { this@mockk.association } returns association
        }
        val donor = mockk<DonorProfile> { every { id } returns UUID.randomUUID() }
        return mockk(relaxed = true) {
            every { providerRef } returns "helloasso:4242"
            every { this@mockk.campaign } returns campaign
            every { this@mockk.donor } returns donor
            every { amount } returns BigDecimal("25.50")
            every { confirmedAt } returns if (confirmed) java.time.Instant.now() else null
        }
    }

    private fun intentWith(vararg states: String) = HelloAssoCheckoutIntentJson(
        id = 4242,
        order = HelloAssoOrderJson(1, 4242, states.mapIndexed { i, s -> HelloAssoPaymentJson(i.toLong(), 2550, s, "Card") }),
    )

    @Test
    fun `Authorized confirms the donation with its payment means`() {
        val donation = donation()
        every { donationRepo.findByProviderRef("helloasso:4242") } returns donation
        every { gateway.getCheckoutIntent("access", "techno-plus", 4242) } returns intentWith("Authorized")

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))
        verify { donation.paymentMethod = "Card" }
        verify { donationService.recordPayment("helloasso:4242", any(), any(), BigDecimal("25.50")) }
    }

    @Test
    fun `16 resent notifications of a paid donation confirm it once`() {
        var confirmed = false
        val donation = donation()
        every { donation.confirmedAt } answers { if (confirmed) java.time.Instant.now() else null }
        every { donationService.recordPayment(any(), any(), any(), any()) } answers { confirmed = true }
        every { donationRepo.findByProviderRef("helloasso:4242") } returns donation
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Authorized")

        repeat(16) { assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242")) }

        verify(exactly = 1) { donationService.recordPayment(any(), any(), any(), any()) }
    }

    @Test
    fun `no order yet, waiting or refused states leave the donation pending`() {
        every { donationRepo.findByProviderRef("helloasso:4242") } returns donation()
        for (intent in listOf(HelloAssoCheckoutIntentJson(id = 4242), intentWith("Pending"), intentWith("WaitingBankValidation"), intentWith("Refused"))) {
            every { gateway.getCheckoutIntent(any(), any(), any()) } returns intent
            assertEquals(HelloAssoPaymentOutcome.PENDING, service.refreshPayment("helloasso:4242"))
        }
        verify(exactly = 0) { donationService.recordPayment(any(), any(), any(), any()) }
    }

    @Test
    fun `a refunded or contested confirmed donation is alerted and journaled, never re-confirmed`() {
        every { donationRepo.findByProviderRef("helloasso:4242") } returns donation(confirmed = true)
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Refunded")

        assertEquals(HelloAssoPaymentOutcome.REVERSED, service.refreshPayment("helloasso:4242"))
        verify { alerts.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, "helloasso:4242", null) }
        verify { auditLog.append(ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED, any(), any(), any(), any()) }
        verify(exactly = 0) { donationService.recordPayment(any(), any(), any(), any()) }
    }
}

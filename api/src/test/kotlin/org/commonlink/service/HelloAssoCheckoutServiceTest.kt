package org.commonlink.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import jakarta.persistence.EntityManager
import org.commonlink.config.HelloAssoProperties
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
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.exception.ConflictException
import org.commonlink.exception.HelloAssoCheckoutRefusedException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoRequestRefusedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.payment.CheckoutPayer
import org.commonlink.repository.ComplianceAuditLogRepository
import org.commonlink.payment.CheckoutRequest
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
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
    private val auditLogRepo = mockk<ComplianceAuditLogRepository> {
        every { findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(any(), any()) } returns null
    }
    private val txManager = mockk<PlatformTransactionManager>(relaxed = true)
    private val entityManager = mockk<EntityManager>(relaxed = true)
    private val fundsRepo = mockk<HelloAssoDonationFundsRepository> {
        every { existsById(any()) } returns false
        every { findByDonationIdForUpdate(any()) } returns null
        every { save(any<HelloAssoDonationFunds>()) } answers { firstArg() }
    }
    private val service = HelloAssoCheckoutService(
        connectionRepo, donationRepo, donationService, tokenManager, gateway, breaker, eligibility, alerts,
        PaymentAmountGuard(alerts, auditLog, auditLogRepo), HelloAssoDonationFundsService(fundsRepo, alerts, auditLog),
        HelloAssoProperties(redirectBaseUrl = "https://app.common-link.test"), TransactionTemplate(txManager), entityManager,
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
    fun `a HelloAsso 400 is our refused request, not an outage`() {
        every { gateway.createCheckoutIntent(any(), any(), any()) } throws HelloAssoRequestRefusedException("POST", 400)

        assertThrows<HelloAssoCheckoutRefusedException> { service.createCheckout(request()) }
        verify(exactly = 0) { breaker.markBroken(any()) }
    }

    @Test
    fun `a rejected token breaks the link and refuses`() {
        every { tokenManager.getValidAccessToken(associationId) } throws HelloAssoUnauthorizedException("refresh_token", 400)

        assertThrows<ConflictException> { service.createCheckout(request()) }
        verify { breaker.markBroken(associationId) }
    }

    // ── Re-read ────────────────────────────────────────────────────────────

    private fun donation(confirmed: Boolean = false, amount: BigDecimal = BigDecimal("25.50")): Donation {
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
            every { this@mockk.amount } returns amount
            every { confirmedAt } returns if (confirmed) java.time.Instant.now() else null
        }
    }

    private fun intentWith(vararg states: String) = HelloAssoCheckoutIntentJson(
        id = 4242,
        order = HelloAssoOrderJson(1, 4242, states.mapIndexed { i, s -> HelloAssoPaymentJson(i.toLong(), 2550, s, "Card") }),
    )

    /** The same donation for the unlocked first read and the locked re-read of the apply step. */
    private fun stubDonation(donation: Donation) {
        every { donationRepo.findByProviderRef("helloasso:4242") } returns donation
        every { donationRepo.findByProviderRefForUpdate("helloasso:4242") } returns donation
    }

    @Test
    fun `Authorized confirms the donation with its payment means`() {
        val donation = donation()
        stubDonation(donation)
        every { gateway.getCheckoutIntent("access", "techno-plus", 4242) } returns intentWith("Authorized")

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))
        verify { donation.paymentMethod = "Card" }
        verify { donationService.recordPayment("helloasso:4242", any(), any(), BigDecimal("25.50")) }
    }

    @Test
    fun `the donation is dated at the HelloAsso payment time`() {
        val donation = donation()
        stubDonation(donation)
        val payment = HelloAssoPaymentJson(1, 2550, "Authorized", "Card", "2026-12-31T23:55:00.1234567+01:00")
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns
            HelloAssoCheckoutIntentJson(id = 4242, order = HelloAssoOrderJson(1, 4242, listOf(payment)))

        service.refreshPayment("helloasso:4242")

        verify {
            donationService.recordPayment(
                "helloasso:4242", any(), any(), BigDecimal("25.50"), Instant.parse("2026-12-31T22:55:00.1234567Z"),
            )
        }
    }

    @Test
    fun `an unreadable payment time confirms without a date`() {
        stubDonation(donation())
        val payment = HelloAssoPaymentJson(1, 2550, "Authorized", "Card", "yesterday")
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns
            HelloAssoCheckoutIntentJson(id = 4242, order = HelloAssoOrderJson(1, 4242, listOf(payment)))

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))
        verify { donationService.recordPayment("helloasso:4242", any(), any(), any(), null) }
    }

    @Test
    fun `a paid amount other than the donation's leaves it pending, alerted and journaled once`() {
        stubDonation(donation())
        val payment = HelloAssoPaymentJson(1, 2500, "Authorized", "Card")
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns
            HelloAssoCheckoutIntentJson(id = 4242, order = HelloAssoOrderJson(1, 4242, listOf(payment)))

        assertEquals(HelloAssoPaymentOutcome.AMOUNT_MISMATCH, service.refreshPayment("helloasso:4242"))
        verify(exactly = 0) { donationService.recordPayment(any(), any(), any(), any(), any()) }
        verify { alerts.reportFailure(TechnicalAlertKind.PAYMENT_AMOUNT_MISMATCH, null, "helloasso:4242", null) }
        verify(exactly = 1) { auditLog.append(ComplianceAuditLogService.PAYMENT_AMOUNT_MISMATCH, any(), any(), any(), any()) }
    }

    /**
     * U9, observed in sandbox on 10/11/2026: a donation paid WITH a voluntary HelloAsso contribution carries the
     * donation amount only (fixture: anonymised order of that payment), so it is confirmed like any other.
     */
    @Test
    fun `a donation paid with a HelloAsso contribution is confirmed at the donation amount`() {
        stubDonation(donation(amount = BigDecimal("10.00")))
        val intent = javaClass.getResourceAsStream("/helloasso/cashout/checkout-intent-with-contribution.json")!!
            .use { jacksonObjectMapper().readValue(it, HelloAssoCheckoutIntentJson::class.java) }
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intent

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))
        verify { donationService.recordPayment("helloasso:4242", any(), any(), BigDecimal("10.00"), any()) }
        verify(exactly = 0) { alerts.reportFailure(TechnicalAlertKind.PAYMENT_AMOUNT_MISMATCH, any(), any(), any()) }
        verify(exactly = 0) { alerts.reportFailure(TechnicalAlertKind.HELLOASSO_ORDER_AMOUNT_DELTA, any(), any(), any()) }
    }

    /** A paid order in the raw shape observed on `GET /v5/orders/{id}`: `amount.total` and `{amount, type}` lines. */
    private fun paidOrder(total: Int? = null, items: List<Pair<Int, String>>? = null) = HelloAssoCheckoutIntentJson(
        id = 4242,
        order = HelloAssoOrderJson(
            1, 4242, listOf(HelloAssoPaymentJson(1, 2550, "Authorized", "Card")),
            total?.let { mapOf("total" to it) }, items?.map { (amount, type) -> mapOf("amount" to amount, "type" to type) },
        ),
    )

    /** An order that does not match its payment is reported, once, and never blocks a payment carrying the donation's amount. */
    @Test
    fun `an order total other than the payment confirms the donation and reports the delta once`() {
        var confirmed = false
        val donation = donation()
        every { donation.confirmedAt } answers { if (confirmed) Instant.now() else null }
        stubDonation(donation)
        every { donationService.recordPayment(any(), any(), any(), any(), any()) } answers { confirmed = true }
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns paidOrder(total = 3050)

        repeat(3) { assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242")) }
        verify(exactly = 1) { alerts.reportFailure(TechnicalAlertKind.HELLOASSO_ORDER_AMOUNT_DELTA, null, "helloasso:4242", null) }
    }

    @Test
    fun `an order with several lines or a non donation line confirms the donation and is reported`() {
        stubDonation(donation())
        for (items in listOf(
            listOf(2550 to "Payment", 500 to "Payment"),
            listOf(2550 to "Donation"),
        )) {
            every { gateway.getCheckoutIntent(any(), any(), any()) } returns paidOrder(total = 2550, items = items)
            assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))
        }
        verify(exactly = 2) { alerts.reportFailure(TechnicalAlertKind.HELLOASSO_ORDER_AMOUNT_DELTA, null, "helloasso:4242", null) }
    }

    @Test
    fun `an order matching its payment, or without total and lines, is not reported`() {
        stubDonation(donation())
        for (intent in listOf(paidOrder(total = 2550, items = listOf(2550 to "Payment")), paidOrder())) {
            every { gateway.getCheckoutIntent(any(), any(), any()) } returns intent
            assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))
        }
        verify(exactly = 0) { alerts.reportFailure(TechnicalAlertKind.HELLOASSO_ORDER_AMOUNT_DELTA, any(), any(), any()) }
    }

    @Test
    fun `a paid amount other than the donation's journals the delta`() {
        stubDonation(donation())
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns
            HelloAssoCheckoutIntentJson(id = 4242, order = HelloAssoOrderJson(1, 4242, listOf(HelloAssoPaymentJson(1, 3050, "Authorized", "Card"))))

        assertEquals(HelloAssoPaymentOutcome.AMOUNT_MISMATCH, service.refreshPayment("helloasso:4242"))
        verify {
            auditLog.append(
                ComplianceAuditLogService.PAYMENT_AMOUNT_MISMATCH, any(),
                match<Map<String, Any?>> { it["delta"] == "5.00" }, any(), any(),
            )
        }
        verify(exactly = 0) { alerts.reportFailure(TechnicalAlertKind.HELLOASSO_ORDER_AMOUNT_DELTA, any(), any(), any()) }
    }

    @Test
    fun `a paid payment without an amount is not confirmed`() {
        stubDonation(donation())
        val payment = HelloAssoPaymentJson(1, null, "Authorized", "Card")
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns
            HelloAssoCheckoutIntentJson(id = 4242, order = HelloAssoOrderJson(1, 4242, listOf(payment)))

        assertEquals(HelloAssoPaymentOutcome.AMOUNT_MISMATCH, service.refreshPayment("helloasso:4242"))
        verify(exactly = 0) { donationService.recordPayment(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `16 resent notifications of a paid donation confirm it once`() {
        var confirmed = false
        val donation = donation()
        every { donation.confirmedAt } answers { if (confirmed) java.time.Instant.now() else null }
        every { donationService.recordPayment(any(), any(), any(), any()) } answers { confirmed = true }
        stubDonation(donation)
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Authorized")

        repeat(16) { assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242")) }

        verify(exactly = 1) { donationService.recordPayment(any(), any(), any(), any()) }
    }

    // ── Re-read: transaction structure (A2/A3) ─────────────────────────────

    @Test
    fun `refreshPayment is not transactional - neither the method nor the class`() {
        val method = HelloAssoCheckoutService::class.java.getMethod("refreshPayment", String::class.java)
        assertNull(method.getAnnotation(Transactional::class.java))
        assertNull(HelloAssoCheckoutService::class.java.getAnnotation(Transactional::class.java))
    }

    @Test
    fun `the token and the HelloAsso re-read happen before the apply transaction, applied under the donation lock`() {
        val donation = donation()
        stubDonation(donation)
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Authorized")

        service.refreshPayment("helloasso:4242")

        verifyOrder {
            tokenManager.getValidAccessToken(associationId)
            gateway.getCheckoutIntent("access", "techno-plus", 4242)
            txManager.getTransaction(any())
            donationRepo.findByProviderRefForUpdate("helloasso:4242")
            entityManager.refresh(donation)
            donationService.recordPayment("helloasso:4242", any(), any(), any())
            txManager.commit(any())
        }
    }

    @Test
    fun `a donation found confirmed once locked is not confirmed again`() {
        val stale = donation()
        val locked = donation(confirmed = true)
        every { donationRepo.findByProviderRef("helloasso:4242") } returns stale
        every { donationRepo.findByProviderRefForUpdate("helloasso:4242") } returns locked
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Authorized")

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))

        verify(exactly = 0) { donationService.recordPayment(any(), any(), any(), any()) }
        verify(exactly = 0) { donationRepo.save(any<Donation>()) }
    }

    @Test
    fun `a HelloAsso failure after a token renewal opens no transaction, so the renewal (own transaction) is kept`() {
        stubDonation(donation())
        every { gateway.getCheckoutIntent(any(), any(), any()) } throws HelloAssoUnavailableException("GET", 502)

        assertThrows<HelloAssoUnavailableException> { service.refreshPayment("helloasso:4242") }

        verify { tokenManager.getValidAccessToken(associationId) }
        verify(exactly = 0) { txManager.getTransaction(any()) }
        verify(exactly = 0) { txManager.rollback(any()) }
    }

    @Test
    fun `a token rejected by the re-read marks the link BROKEN outside any transaction and rethrows`() {
        stubDonation(donation())
        every { gateway.getCheckoutIntent(any(), any(), any()) } throws HelloAssoUnauthorizedException("GET", 401)

        assertThrows<HelloAssoUnauthorizedException> { service.refreshPayment("helloasso:4242") }

        verify { breaker.markBroken(associationId) }
        verify(exactly = 0) { txManager.getTransaction(any()) }
    }

    @Test
    fun `a rejected refresh token during the re-read marks the link BROKEN and refuses`() {
        stubDonation(donation())
        every { tokenManager.getValidAccessToken(associationId) } throws HelloAssoUnauthorizedException("refresh_token", 400)

        assertThrows<ConflictException> { service.refreshPayment("helloasso:4242") }

        verify { breaker.markBroken(associationId) }
        verify(exactly = 0) { gateway.getCheckoutIntent(any(), any(), any()) }
        verify(exactly = 0) { txManager.getTransaction(any()) }
    }

    @Test
    fun `no order yet, waiting or refused states leave the donation pending`() {
        stubDonation(donation())
        for (intent in listOf(HelloAssoCheckoutIntentJson(id = 4242), intentWith("Pending"), intentWith("WaitingBankValidation"), intentWith("Refused"))) {
            every { gateway.getCheckoutIntent(any(), any(), any()) } returns intent
            assertEquals(HelloAssoPaymentOutcome.PENDING, service.refreshPayment("helloasso:4242"))
        }
        verify(exactly = 0) { donationService.recordPayment(any(), any(), any(), any()) }
    }

    @Test
    fun `a refunded or contested confirmed donation is alerted and journaled, never re-confirmed`() {
        stubDonation(donation(confirmed = true))
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Refunded")

        assertEquals(HelloAssoPaymentOutcome.REVERSED, service.refreshPayment("helloasso:4242"))
        verify { alerts.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, "helloasso:4242", null) }
        verify { auditLog.append(ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED, any(), any(), any(), any()) }
        verify(exactly = 0) { donationService.recordPayment(any(), any(), any(), any()) }
    }

    // ── Cash-out tracking (FD3-05) ─────────────────────────────────────────

    private val donationId = UUID.randomUUID()

    private fun trackedDonation(confirmed: Boolean = false) = donation(confirmed).also { every { it.id } returns donationId }

    private fun fundsRow(state: FundsState) = HelloAssoDonationFunds(
        donation = mockk(relaxed = true), haPaymentId = 7, cashOutState = "Transfered", fundsState = state,
    )

    @Test
    fun `the confirmation tracks the payment that confirmed the donation, in its payout state`() {
        stubDonation(trackedDonation())
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns HelloAssoCheckoutIntentJson(
            id = 4242,
            order = HelloAssoOrderJson(1, 4242, listOf(HelloAssoPaymentJson(7, 2550, "Authorized", "Card", cashOutState = "Transfered"))),
        )
        val saved = slot<HelloAssoDonationFunds>()
        every { fundsRepo.save(capture(saved)) } answers { firstArg() }

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))

        assertEquals(7L, saved.captured.haPaymentId)
        assertEquals(FundsState.AT_PROVIDER, saved.captured.fundsState)
        assertEquals("Transfered", saved.captured.cashOutState)
        assertNull(saved.captured.unknownSince)
        verifyOrder {
            donationService.recordPayment("helloasso:4242", any(), any(), any(), any())
            fundsRepo.save(any<HelloAssoDonationFunds>())
        }
    }

    @Test
    fun `an existing tracking row is left unchanged`() {
        stubDonation(trackedDonation())
        every { fundsRepo.existsById(donationId) } returns true
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Authorized")

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))
        verify(exactly = 0) { fundsRepo.save(any<HelloAssoDonationFunds>()) }
    }

    @Test
    fun `an order with several payments is alerted, confirmed and tracked on the paid one`() {
        stubDonation(trackedDonation())
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Refused", "Authorized")
        val saved = slot<HelloAssoDonationFunds>()
        every { fundsRepo.save(capture(saved)) } answers { firstArg() }

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))

        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_ORDER_SEVERAL_PAYMENTS, "helloasso:4242", "2 paiements") }
        assertEquals(1L, saved.captured.haPaymentId)
    }

    @Test
    fun `a single payment raises no several-payments alert`() {
        stubDonation(trackedDonation())
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Authorized")

        service.refreshPayment("helloasso:4242")
        verify(exactly = 0) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_ORDER_SEVERAL_PAYMENTS, any(), any()) }
    }

    @Test
    fun `refunding a donation still at HelloAsso withdraws it, one alert, journaled with its previous state`() {
        assertReversalWithdraws(FundsState.AT_PROVIDER)
    }

    @Test
    fun `refunding an available donation withdraws it, one alert, journaled with its previous state`() {
        assertReversalWithdraws(FundsState.AVAILABLE)
    }

    private fun assertReversalWithdraws(previous: FundsState) {
        stubDonation(trackedDonation(confirmed = true))
        val row = fundsRow(previous)
        every { fundsRepo.findByDonationIdForUpdate(donationId) } returns row
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Refunded")
        val payload = slot<Any>()
        every { auditLog.append(ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED, any(), capture(payload), any(), any()) } returns mockk(relaxed = true)

        assertEquals(HelloAssoPaymentOutcome.REVERSED, service.refreshPayment("helloasso:4242"))

        assertEquals(FundsState.WITHDRAWN, row.fundsState)
        verify(exactly = 1) { alerts.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, "helloasso:4242", null) }
        val journaled = payload.captured as Map<*, *>
        assertEquals(previous.name, journaled["previousFundsState"])
        assertEquals("Refunded", journaled["state"])
    }

    @Test
    fun `a reversal reported again on a withdrawn donation is alerted again but journaled once`() {
        stubDonation(trackedDonation(confirmed = true))
        every { fundsRepo.findByDonationIdForUpdate(donationId) } returns fundsRow(FundsState.WITHDRAWN)
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns intentWith("Refunded")

        assertEquals(HelloAssoPaymentOutcome.REVERSED, service.refreshPayment("helloasso:4242"))
        verify(exactly = 1) { alerts.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, "helloasso:4242", null) }
        verify(exactly = 0) { auditLog.append(ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED, any(), any(), any(), any()) }
    }

    @Test
    fun `a paying payment without id still confirms the donation, untracked and alerted`() {
        stubDonation(trackedDonation())
        every { gateway.getCheckoutIntent(any(), any(), any()) } returns HelloAssoCheckoutIntentJson(
            id = 4242, order = HelloAssoOrderJson(1, 4242, listOf(HelloAssoPaymentJson(null, 2550, "Authorized", "Card"))),
        )

        assertEquals(HelloAssoPaymentOutcome.CONFIRMED, service.refreshPayment("helloasso:4242"))

        verify { donationService.recordPayment("helloasso:4242", any(), any(), any(), any()) }
        verify(exactly = 0) { fundsRepo.save(any<HelloAssoDonationFunds>()) }
        verify(exactly = 1) { alerts.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNTRACKED, "helloasso:4242", any()) }
    }
}

package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.commonlink.config.HelloAssoMockGuard
import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoInitCheckoutJson
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.core.env.Environment

/** The fake answers like HelloAsso for each scenario, and can never run under prod. */
class FakeHelloAssoGatewayTest {

    private val fake = FakeHelloAssoGateway()
    private val body = HelloAssoInitCheckoutJson(
        2500, 2500, "Don - X", "https://app/r?cancelled=true", "https://app/r?type=error", "https://app/r?ref=abc", true,
        metadata = mapOf("publicRef" to "abc"),
    )

    @Test
    fun `mock keys resolve to one organization carrying the RNA, other keys are refused`() {
        val tokens = fake.obtainTokens("mock:W751121684", "anything")
        val orgs = fake.listOrganizations(tokens.accessToken)

        assertEquals("mock-w751121684", orgs.single().organizationSlug)
        assertEquals("W751121684", fake.getOrganization(tokens.accessToken, "mock-w751121684").rnaNumber)
        assertThrows<HelloAssoUnauthorizedException> { fake.obtainTokens("real-client-id", "secret") }
        assertThrows<HelloAssoUnauthorizedException> { fake.obtainTokens("mock:W751121684", FakeHelloAssoGateway.INVALID_SECRET) }
    }

    @Test
    fun `eligibility is driven, and a checkout is refused while not eligible`() {
        val tokens = fake.obtainTokens("mock:W751121684", "s")
        val slug = "mock-w751121684"
        assertEquals(false, fake.getOrganization(tokens.accessToken, slug).cashInCompliant)
        assertThrows<HelloAssoNotEligibleException> { fake.createCheckoutIntent(tokens.accessToken, slug, body) }

        fake.setCashIn(slug, true)
        assertEquals(true, fake.getOrganization(tokens.accessToken, slug).cashInCompliant)
    }

    @Test
    fun `a checkout redirects to the local return page and is paid unless told otherwise`() {
        val tokens = fake.obtainTokens("mock:W751121684", "s")
        fake.setCashIn("mock-w751121684", true)

        val created = fake.createCheckoutIntent(tokens.accessToken, "mock-w751121684", body)

        assertTrue(created.redirectUrl!!.startsWith("https://app/r?ref=abc&checkoutIntentId="))
        assertEquals("Authorized", fake.getCheckoutIntent(tokens.accessToken, "mock-w751121684", created.id!!).order!!.payments!!.single().state)

        fake.setPaymentState(created.id!!, "Refused")
        assertNull(fake.getCheckoutIntent(tokens.accessToken, "mock-w751121684", created.id!!).order)
    }

    @Test
    fun `a payment starts Transfered, its payout state is driven and read by E1 and E2`() {
        val tokens = fake.obtainTokens("mock:W751121684", "s")
        fake.setCashIn("mock-w751121684", true)
        val id = fake.createCheckoutIntent(tokens.accessToken, "mock-w751121684", body).id!!

        val initial = fake.getPayment(tokens.accessToken, id)
        assertEquals("Transfered", initial.cashOutState)
        assertEquals(2500, initial.amount)
        assertNull(initial.idCashOut)

        assertTrue(fake.setCashOut(id, "CashedOut", "2026-11-12T10:00:00+01:00", 60001))
        val paidOut = fake.getPayment(tokens.accessToken, id)
        assertEquals("CashedOut", paidOut.cashOutState)
        assertEquals("2026-11-12T10:00:00+01:00", paidOut.cashOutDate)
        assertEquals(60001L, paidOut.idCashOut)
        val e1 = fake.getCheckoutIntent(tokens.accessToken, "mock-w751121684", id).order!!.payments!!.single()
        assertEquals("CashedOut", e1.cashOutState)
        assertEquals(60001L, e1.idCashOut)

        assertFalse(fake.setCashOut(999_999, "CashedOut"))
        assertThrows<HelloAssoUnauthorizedException> { fake.getPayment("unknown-token", id) }
    }

    @Test
    fun `refresh rotates the pair, an unknown refresh token is rejected`() {
        val tokens = fake.obtainTokens("mock:W751121684", "s")
        assertDoesNotThrow { fake.refreshTokens(tokens.refreshToken) }
        assertThrows<HelloAssoUnauthorizedException> { fake.refreshTokens("unknown") }
    }

    @Test
    fun `the mock flag refuses to start under prod`() {
        val prod = mockk<Environment> { every { activeProfiles } returns arrayOf("prod") }
        val local = mockk<Environment> { every { activeProfiles } returns arrayOf("local") }

        assertThrows<IllegalStateException> { HelloAssoMockGuard(HelloAssoProperties(mock = true), prod).check() }
        assertDoesNotThrow { HelloAssoMockGuard(HelloAssoProperties(mock = false), prod).check() }
        assertDoesNotThrow { HelloAssoMockGuard(HelloAssoProperties(mock = true), local).check() }
    }
}

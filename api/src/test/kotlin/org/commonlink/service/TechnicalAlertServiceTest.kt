package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.config.TechnicalAlertProperties
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration

/**
 * Unit tests for [TechnicalAlertService].
 *
 * Instantiated directly rather than through Spring: `@Async` is a proxy concern, and calling the
 * bean directly makes every assertion synchronous. What is under test is the throttling arithmetic
 * and the "never throw at the caller" contract, neither of which involves the proxy.
 *
 * No test manipulates a clock. Cooldown and window are configuration, so a test that wants the
 * cooldown to have elapsed sets it to zero — which is also the honest way to prove the boundary
 * condition is `>=` rather than `>`.
 */
class TechnicalAlertServiceTest {

    private val emailService = mockk<EmailService>(relaxed = true)

    private fun service(
        notificationEmail: String = "dev@common-link.org",
        alertsEnabled: Boolean = true,
        cooldown: Duration = Duration.ofMinutes(30),
        burstThreshold: Int = 3,
        burstWindow: Duration = Duration.ofMinutes(5),
    ) = TechnicalAlertService(
        emailService,
        TechnicalAlertProperties(notificationEmail, alertsEnabled, cooldown, burstThreshold, burstWindow),
    )

    @Test
    fun `reports a failure on first occurrence`() {
        service().reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "POST", "/api/donations", RuntimeException("boom"))

        verify(exactly = 1) {
            emailService.sendTechnicalAlert(
                recipientEmail = "dev@common-link.org",
                severity = "ERROR",
                title = any(),
                context = any(),
                stackTrace = any(),
            )
        }
    }

    @Test
    fun `suppresses a repeat of the same signature while the cooldown runs`() {
        val service = service()
        repeat(5) {
            service.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/campaigns", IllegalStateException("boom"))
        }

        verify(exactly = 1) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sends again once the cooldown has elapsed`() {
        val service = service(cooldown = Duration.ZERO)
        repeat(3) {
            service.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/campaigns", IllegalStateException("boom"))
        }

        verify(exactly = 3) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `treats a different exception class as a different signature`() {
        val service = service()
        service.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/a", IllegalStateException("a"))
        service.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/b", IllegalArgumentException("b"))

        verify(exactly = 2) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `does not key the cooldown on the request path`() {
        // A path-keyed cache would send one e-mail per URL, which is precisely the flood the
        // throttling exists to prevent — and on the public widget the path is attacker-chosen.
        val service = service()
        repeat(50) { i ->
            service.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/campaigns/$i", RuntimeException("boom"))
        }

        verify(exactly = 1) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `stays silent for a client that hung up`() {
        val service = service()
        service.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/widget", IOException("Broken pipe"))
        service.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/widget", RuntimeException("wrapped", IOException("Connection reset by peer")))

        verify(exactly = 0) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `survives a cyclic cause chain`() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        service().reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/x", first)

        verify(exactly = 1) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `raises a burst alert only once the threshold is reached`() {
        val service = service(burstThreshold = 3)

        service.reportBurst(TechnicalAlertKind.ACCESS_DENIED_BURST, "GET", "/api/admin")
        service.reportBurst(TechnicalAlertKind.ACCESS_DENIED_BURST, "GET", "/api/admin")
        verify(exactly = 0) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }

        service.reportBurst(TechnicalAlertKind.ACCESS_DENIED_BURST, "GET", "/api/admin")

        val context = slotOfContext()
        verify(exactly = 1) {
            emailService.sendTechnicalAlert(any(), "WARN", any(), capture(context), null)
        }
        assertThat(context.captured["Occurrences"]).startsWith("3 ")
    }

    @Test
    fun `counts each burst kind independently`() {
        val service = service(burstThreshold = 2)
        service.reportBurst(TechnicalAlertKind.ACCESS_DENIED_BURST, "GET", "/api/admin")
        service.reportBurst(TechnicalAlertKind.RATE_LIMIT_BURST, "POST", "/api/auth/login")

        verify(exactly = 0) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sends nothing when no mailbox is configured`() {
        service(notificationEmail = "").reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/x", RuntimeException("boom"))

        verify(exactly = 0) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sends nothing when alerting is switched off`() {
        service(alertsEnabled = false).reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/x", RuntimeException("boom"))

        verify(exactly = 0) { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `never propagates a mail failure to the caller`() {
        // The caller is an exception handler. An exception escaping here would replace a handled
        // failure with an unhandled one and lose the original error.
        every { emailService.sendTechnicalAlert(any(), any(), any(), any(), any()) } throws RuntimeException("SMTP down")

        service().reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/x", RuntimeException("boom"))
        service(burstThreshold = 1).reportBurst(TechnicalAlertKind.RATE_LIMIT_BURST, "POST", "/api/auth/login")
    }

    private fun slotOfContext() = io.mockk.slot<Map<String, String>>()
}

package org.commonlink.exception

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.service.TechnicalAlertKind
import org.commonlink.service.TechnicalAlertService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException

/**
 * Proves that [GlobalExceptionHandler] actually calls [TechnicalAlertService], and that it calls it
 * for the right handlers only.
 *
 * ### Why this is not part of GlobalExceptionHandlerTest
 * That class is a `@WebMvcTest` slice, and a slice never declares a [TechnicalAlertService] bean —
 * the handler's [ObjectProvider] resolves to nothing there, so every assertion in it exercises the
 * *absent-alerting* branch. Left at that, no test in the repository would ever have constructed the
 * handler with an alerting collaborator present, and "alerting works" would be an untested claim.
 * Instantiating the handler directly is the cheapest way to close that gap and needs no context.
 */
class GlobalExceptionHandlerAlertingTest {

    private val alertService = mockk<TechnicalAlertService>(relaxed = true)

    private fun handler(alerting: TechnicalAlertService? = alertService): GlobalExceptionHandler {
        val provider = mockk<ObjectProvider<TechnicalAlertService>>()
        every { provider.ifAvailable } returns alerting
        return GlobalExceptionHandler(provider)
    }

    private fun request(method: String = "GET", uri: String = "/api/campaigns/42", query: String? = null) =
        MockHttpServletRequest(method, uri).apply { queryString = query }

    @Test
    fun `reports an unhandled exception`() {
        val ex = IllegalStateException("boom")

        val response = handler().handleGeneric(ex, request("POST", "/api/donations"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        verify(exactly = 1) {
            alertService.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "POST", "/api/donations", ex)
        }
    }

    @Test
    fun `reports a Mollie payment failure`() {
        val ex = MolliePaymentException("gateway down")

        val response = handler().handleMolliePayment(ex, request("POST", "/api/widget/donations"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        verify(exactly = 1) {
            alertService.reportFailure(TechnicalAlertKind.PAYMENT_GATEWAY_FAILURE, "POST", "/api/widget/donations", ex)
        }
    }

    @Test
    fun `reports an unreachable upstream`() {
        val ex = BadGatewayException("INSEE API temporarily unavailable")

        val response = handler().handleBadGateway(ex, request("GET", "/api/sirene/search"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        verify(exactly = 1) {
            alertService.reportFailure(TechnicalAlertKind.UPSTREAM_UNAVAILABLE, "GET", "/api/sirene/search", ex)
        }
    }

    @Test
    fun `counts an access denial towards a burst rather than reporting it outright`() {
        val response = handler().handleAccessDenied(AccessDeniedException("nope"), request("GET", "/api/admin/users"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        verify(exactly = 1) {
            alertService.reportBurst(TechnicalAlertKind.ACCESS_DENIED_BURST, "GET", "/api/admin/users")
        }
        verify(exactly = 0) { alertService.reportFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `counts a rate limit towards a burst rather than reporting it outright`() {
        val response = handler().handleRateLimit(RateLimitException(), request("POST", "/api/auth/login"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        verify(exactly = 1) {
            alertService.reportBurst(TechnicalAlertKind.RATE_LIMIT_BURST, "POST", "/api/auth/login")
        }
        verify(exactly = 0) { alertService.reportFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `never reports a user mistake`() {
        // The whole value of the channel is that its contents are actionable. One 404 or one
        // rejected campaign edit reaching a developer mailbox is how an alert channel dies.
        val instance = handler()
        instance.handleNotFound(NotFoundException("no such campaign"))
        instance.handleConflict(ConflictException("email already used"))
        instance.handleUnprocessableEntity(UnprocessableEntityException("Campaign goal must be greater than zero"), request())
        instance.handleIllegalArgument(IllegalArgumentException("bad uuid"))

        verify(exactly = 0) { alertService.reportFailure(any(), any(), any(), any()) }
        verify(exactly = 0) { alertService.reportBurst(any(), any(), any()) }
    }

    @Test
    fun `never passes the query string to the alerting channel`() {
        // Query strings on this API carry verification tokens and e-mail addresses, and e-mail is
        // not an access-controlled channel.
        val ex = IllegalStateException("boom")

        handler().handleGeneric(ex, request("GET", "/api/auth/verify", query = "token=secret&email=donor@example.org"))

        verify(exactly = 1) {
            alertService.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, "GET", "/api/auth/verify", ex)
        }
    }

    @Test
    fun `still answers when no alerting bean is available`() {
        // The @WebMvcTest slices run in exactly this configuration, and so would any deployment
        // whose profile resolves no EmailService.
        val response = handler(alerting = null).handleGeneric(RuntimeException("boom"), request())

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `still answers when the alerting collaborator throws`() {
        // An exception escaping here would replace a handled 500 with an unhandled one.
        every { alertService.reportFailure(any(), any(), any(), any()) } throws RuntimeException("alerting broken")

        val response = handler().handleGeneric(RuntimeException("boom"), request())

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `tolerates a missing request`() {
        val response = handler().handleGeneric(RuntimeException("boom"), null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        verify(exactly = 1) {
            alertService.reportFailure(TechnicalAlertKind.UNHANDLED_EXCEPTION, null, "(unknown)", any())
        }
    }
}

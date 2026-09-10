package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.commonlink.config.JacksonConfig
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.BridgeWebhookService
import org.commonlink.service.BridgeWebhookSignatureVerifier
import org.commonlink.service.TechnicalAlertKind
import org.commonlink.service.TechnicalAlertService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Covers the two independent layers described in [BridgeWebhookController]'s KDoc: signature
 * verification gates the request before anything else runs, and only then is the (still
 * untrusted) body parsed and handed to [BridgeWebhookService].
 */
@WebMvcTest(BridgeWebhookController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class, JacksonConfig::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class BridgeWebhookControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var bridgeWebhookService: BridgeWebhookService

    @MockkBean
    private lateinit var signatureVerifier: BridgeWebhookSignatureVerifier

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    @MockkBean
    private lateinit var technicalAlertService: TechnicalAlertService

    private fun postWebhook(body: String, signatureHeader: String? = "v1=irrelevant-in-this-test") =
        mockMvc.perform(
            post("/api/public/webhooks/bridge")
                .contentType(MediaType.APPLICATION_JSON)
                .apply { if (signatureHeader != null) header("BridgeApi-Signature", signatureHeader) }
                .content(body)
        )

    @Test
    fun `returns 401 and never calls the service when the signature is invalid`() {
        every { signatureVerifier.isValid(any(), any()) } returns false

        postWebhook("""{"type":"payment.link.updated","payment_link_id":"pl_1"}""")
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { bridgeWebhookService.handlePaymentLinkNotification(any(), any()) }
    }

    @Test
    fun `returns 200 and does nothing for a TEST_EVENT with no identifying field`() {
        every { signatureVerifier.isValid(any(), any()) } returns true

        postWebhook("""{"type":"TEST_EVENT"}""").andExpect(status().isOk)

        verify(exactly = 0) { bridgeWebhookService.handlePaymentLinkNotification(any(), any()) }
    }

    @Test
    fun `dispatches to the service with the payment_link_id when present`() {
        every { signatureVerifier.isValid(any(), any()) } returns true
        every { bridgeWebhookService.handlePaymentLinkNotification("pl_1", null) } just Runs

        postWebhook("""{"type":"payment.link.updated","payment_link_id":"pl_1"}""")
            .andExpect(status().isOk)

        verify(exactly = 1) { bridgeWebhookService.handlePaymentLinkNotification("pl_1", null) }
    }

    @Test
    fun `falls back to client_reference when payment_link_id is absent`() {
        every { signatureVerifier.isValid(any(), any()) } returns true
        every { bridgeWebhookService.handlePaymentLinkNotification(null, "payout-42") } just Runs

        postWebhook("""{"type":"payment.transaction.updated","client_reference":"payout-42"}""")
            .andExpect(status().isOk)

        verify(exactly = 1) { bridgeWebhookService.handlePaymentLinkNotification(null, "payout-42") }
    }

    @Test
    fun `returns 502 so Bridge retries when processing fails`() {
        every { signatureVerifier.isValid(any(), any()) } returns true
        every { bridgeWebhookService.handlePaymentLinkNotification("pl_err", null) } throws RuntimeException("boom")
        every { technicalAlertService.reportFailure(any(), any(), any(), any()) } just Runs

        postWebhook("""{"type":"payment.link.updated","payment_link_id":"pl_err"}""")
            .andExpect(status().isBadGateway)

        verify(exactly = 1) {
            technicalAlertService.reportFailure(
                TechnicalAlertKind.WEBHOOK_PROCESSING_FAILURE,
                "POST",
                "/api/public/webhooks/bridge",
                any(),
            )
        }
    }

    @Test
    fun `endpoint is accessible without authentication`() {
        every { signatureVerifier.isValid(any(), any()) } returns true
        every { bridgeWebhookService.handlePaymentLinkNotification("pl_pub", null) } just Runs

        postWebhook("""{"type":"payment.link.updated","payment_link_id":"pl_pub"}""")
            .andExpect(status().isOk)
    }
}

package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.commonlink.exception.MolliePaymentException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.MollieWebhookService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(MollieWebhookController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class MollieWebhookControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var mollieWebhookService: MollieWebhookService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @MockkBean
    private lateinit var userRepository: UserRepository

    private fun postWebhook(id: String) =
        mockMvc.perform(
            post("/api/public/webhooks/mollie")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id", id)
        )

    @Test
    fun `returns 200 for paid payment`() {
        every { mollieWebhookService.handleWebhook("tr_paid") } just Runs
        postWebhook("tr_paid").andExpect(status().isOk)
    }

    @Test
    fun `returns 200 for canceled payment`() {
        every { mollieWebhookService.handleWebhook("tr_canceled") } just Runs
        postWebhook("tr_canceled").andExpect(status().isOk)
    }

    @Test
    fun `returns 200 for expired payment`() {
        every { mollieWebhookService.handleWebhook("tr_expired") } just Runs
        postWebhook("tr_expired").andExpect(status().isOk)
    }

    @Test
    fun `returns 200 for failed payment`() {
        every { mollieWebhookService.handleWebhook("tr_failed") } just Runs
        postWebhook("tr_failed").andExpect(status().isOk)
    }

    @Test
    fun `returns 200 for open payment (no-op)`() {
        every { mollieWebhookService.handleWebhook("tr_open") } just Runs
        postWebhook("tr_open").andExpect(status().isOk)
    }

    @Test
    fun `returns 200 for authorized payment (still pending)`() {
        every { mollieWebhookService.handleWebhook("tr_authorized") } just Runs
        postWebhook("tr_authorized").andExpect(status().isOk)
    }

    @Test
    fun `returns 200 even when service throws MolliePaymentException`() {
        every { mollieWebhookService.handleWebhook("tr_err") } throws
            MolliePaymentException("Mollie unreachable")
        postWebhook("tr_err").andExpect(status().isOk)
    }

    @Test
    fun `returns 200 even when service throws unexpected exception`() {
        every { mollieWebhookService.handleWebhook("tr_crash") } throws RuntimeException("boom")
        postWebhook("tr_crash").andExpect(status().isOk)
    }

    @Test
    fun `returns 400 when id param is missing`() {
        mockMvc.perform(
            post("/api/public/webhooks/mollie")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `endpoint is accessible without authentication`() {
        every { mollieWebhookService.handleWebhook("tr_public") } just Runs
        postWebhook("tr_public").andExpect(status().isOk)
    }

    @Test
    fun `service is called exactly once per webhook`() {
        every { mollieWebhookService.handleWebhook("tr_once") } just Runs
        postWebhook("tr_once").andExpect(status().isOk)
        verify(exactly = 1) { mollieWebhookService.handleWebhook("tr_once") }
    }
}

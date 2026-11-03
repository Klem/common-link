package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.repository.UserRepository
import org.commonlink.security.ClientIpResolver
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.HelloAssoNotificationOutcome
import org.commonlink.service.HelloAssoNotificationService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HelloAssoWebhookController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class HelloAssoWebhookControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var notificationService: HelloAssoNotificationService
    @MockkBean private lateinit var clientIpResolver: ClientIpResolver
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val body = """{"eventType":"Order","data":{"checkoutIntentId":4242}}"""

    private fun notify() = post("/api/public/webhooks/helloasso/tok").contentType(MediaType.APPLICATION_JSON).content(body)

    @Test
    fun `a HelloAsso IP gets 200 without authentication and the body is handed over`() {
        every { clientIpResolver.resolve(any()) } returns "4.233.135.234"
        every { notificationService.isAllowedSource("4.233.135.234") } returns true
        every { notificationService.receive("tok", body) } returns HelloAssoNotificationOutcome.PAYMENT

        mockMvc.perform(notify()).andExpect(status().isOk)
        verify { notificationService.receive("tok", body) }
    }

    @Test
    fun `another IP is refused before anything is processed`() {
        every { clientIpResolver.resolve(any()) } returns "203.0.113.9"
        every { notificationService.isAllowedSource("203.0.113.9") } returns false

        mockMvc.perform(notify()).andExpect(status().isForbidden)
        verify(exactly = 0) { notificationService.receive(any(), any()) }
    }

    @Test
    fun `a processing failure still answers 200 so HelloAsso does not resend`() {
        every { clientIpResolver.resolve(any()) } returns "4.233.135.234"
        every { notificationService.isAllowedSource(any()) } returns true
        every { notificationService.receive(any(), any()) } throws IllegalStateException("boom")

        mockMvc.perform(notify()).andExpect(status().isOk)
    }
}

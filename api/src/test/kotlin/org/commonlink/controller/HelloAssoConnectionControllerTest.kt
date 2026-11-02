package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.HelloAssoNotificationUrlResponse
import org.commonlink.dto.HelloAssoVerifyRequest
import org.commonlink.dto.HelloAssoVerifyResponse
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.HelloAssoInvalidKeysException
import org.commonlink.exception.HelloAssoNotificationUrlNotConfiguredException
import org.commonlink.exception.HelloAssoOrganizationUnresolvedException
import org.commonlink.exception.RateLimitException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.HelloAssoCredentialsService
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(HelloAssoConnectionController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class HelloAssoConnectionControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var service: HelloAssoCredentialsService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000007")
    private val secret = "SECRET-entered-by-the-association"
    private val body = """{"clientId":"client_123","clientSecret":"$secret"}"""
    private val verifyUrl = "/api/association/helloasso/credentials/verify"

    private fun verifyAs(role: String) =
        post(verifyUrl).with(user(userId.toString()).roles(role)).contentType(MediaType.APPLICATION_JSON).content(body)

    @Test
    fun `verify - 401 without JWT`() {
        mockMvc.perform(post(verifyUrl).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `verify - 403 for DONOR, service never called`() {
        mockMvc.perform(verifyAs("DONOR")).andExpect(status().isForbidden)
        verify(exactly = 0) { service.verify(any(), any()) }
    }

    @Test
    fun `verify - 200 with the verification`() {
        val verifyId = UUID.randomUUID()
        every { service.verify(userId, HelloAssoVerifyRequest("client_123", secret)) } returns
            HelloAssoVerifyResponse(verifyId, "Techno Plus", "techno-plus", matchesKyb = true, canCollect = true)

        mockMvc.perform(verifyAs("ASSOCIATION"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verifyId").value(verifyId.toString()))
            .andExpect(jsonPath("$.matchesKyb").value(true))
            .andExpect(content().string(not(containsString(secret))))
    }

    @Test
    fun `verify - 200 with matchesKyb false for another organization`() {
        every { service.verify(userId, any()) } returns
            HelloAssoVerifyResponse(null, "Other", "other", matchesKyb = false, canCollect = true)

        mockMvc.perform(verifyAs("ASSOCIATION"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matchesKyb").value(false))
            .andExpect(jsonPath("$.verifyId").doesNotExist())
    }

    @Test
    fun `verify - 422 with code for invalid keys, secret not echoed`() {
        every { service.verify(userId, any()) } throws HelloAssoInvalidKeysException()

        mockMvc.perform(verifyAs("ASSOCIATION"))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("HELLOASSO_INVALID_KEYS"))
            .andExpect(content().string(not(containsString(secret))))
    }

    @Test
    fun `verify - 422 with code for an unresolved organization`() {
        every { service.verify(userId, any()) } throws HelloAssoOrganizationUnresolvedException()

        mockMvc.perform(verifyAs("ASSOCIATION"))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("HELLOASSO_ORGANIZATION_UNRESOLVED"))
    }

    @Test
    fun `verify - 422 without secret, field-level error never echoes values`() {
        mockMvc.perform(
            post(verifyUrl).with(user(userId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON).content("""{"clientId":"client_123","clientSecret":""}""")
        )
            .andExpect(status().isUnprocessableContent)
            .andExpect(content().string(not(containsString("client_123"))))
        verify(exactly = 0) { service.verify(any(), any()) }
    }

    @Test
    fun `verify - 429 when rate limited`() {
        every { service.verify(userId, any()) } throws RateLimitException()

        mockMvc.perform(verifyAs("ASSOCIATION")).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `verify - 502 when HelloAsso is unavailable, secret not echoed`() {
        every { service.verify(userId, any()) } throws BadGatewayException("HelloAsso is unavailable")

        mockMvc.perform(verifyAs("ASSOCIATION"))
            .andExpect(status().isBadGateway)
            .andExpect(content().string(not(containsString(secret))))
    }

    @Test
    fun `notification-url - 200 with the URL`() {
        every { service.ensureNotificationUrl(userId) } returns
            HelloAssoNotificationUrlResponse("https://api.common-link.test/api/public/webhooks/helloasso/tok")

        mockMvc.perform(post("/api/association/helloasso/notification-url").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notificationUrl").value("https://api.common-link.test/api/public/webhooks/helloasso/tok"))
    }

    @Test
    fun `notification-url - 503 with code when not configured`() {
        every { service.ensureNotificationUrl(userId) } throws HelloAssoNotificationUrlNotConfiguredException()

        mockMvc.perform(post("/api/association/helloasso/notification-url").with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("HELLOASSO_NOTIFICATION_URL_NOT_CONFIGURED"))
    }

    @Test
    fun `notification-url - 401 without JWT`() {
        mockMvc.perform(post("/api/association/helloasso/notification-url")).andExpect(status().isUnauthorized)
    }
}

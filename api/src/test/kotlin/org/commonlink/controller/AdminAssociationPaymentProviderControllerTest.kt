package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.PaymentProviderOptionDto
import org.commonlink.dto.PaymentProviderPreviewDto
import org.commonlink.exception.NotFoundException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.payment.ProviderReadinessStatus
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.PaymentProviderChangeService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(AdminAssociationPaymentProviderController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AdminAssociationPaymentProviderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var service: PaymentProviderChangeService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val associationId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val curatorId = UUID.fromString("00000000-0000-0000-0000-000000000009")
    private val url = "/api/admin/associations/$associationId/payment-provider"

    private fun preview(current: PaymentProviderId) = PaymentProviderPreviewDto(
        associationId = associationId,
        currentProvider = current,
        providers = listOf(
            PaymentProviderOptionDto(PaymentProviderId.MOLLIE, ProviderReadinessStatus.READY),
            PaymentProviderOptionDto(PaymentProviderId.HELLOASSO, null),
        ),
        liveCampaigns = 2,
        widgetEnabled = true,
        pendingDonations = 1,
    )

    private fun body(reason: String) = """{"provider":"MOLLIE","reason":"$reason"}"""

    // ── Security ───────────────────────────────────────────────────────────

    @Test
    fun `preview - 401 without JWT`() {
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `preview - 403 for ASSOCIATION role`() {
        mockMvc.perform(get(url).with(user("assoc-user").roles("ASSOCIATION")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `change - 403 for ASSOCIATION role, service never called`() {
        mockMvc.perform(
            put(url).with(user(curatorId.toString()).roles("ASSOCIATION"))
                .contentType(MediaType.APPLICATION_JSON).content(body("asked by the association"))
        ).andExpect(status().isForbidden)

        verify(exactly = 0) { service.change(any(), any(), any(), any()) }
    }

    // ── Preview ────────────────────────────────────────────────────────────

    @Test
    fun `preview - 200 for CURATOR`() {
        every { service.preview(associationId) } returns preview(PaymentProviderId.HELLOASSO)

        mockMvc.perform(get(url).with(user(curatorId.toString()).roles("CURATOR")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentProvider").value("HELLOASSO"))
            .andExpect(jsonPath("$.providers[0].readiness").value("READY"))
            .andExpect(jsonPath("$.liveCampaigns").value(2))
    }

    @Test
    fun `preview - 404 for an unknown association`() {
        every { service.preview(associationId) } throws NotFoundException("Association not found")

        mockMvc.perform(get(url).with(user(curatorId.toString()).roles("CURATOR")))
            .andExpect(status().isNotFound)
    }

    // ── Change ─────────────────────────────────────────────────────────────

    @Test
    fun `change - 200 with the updated preview, curator taken from the principal`() {
        every {
            service.change(associationId, PaymentProviderId.MOLLIE, "asked by the association", curatorId)
        } returns preview(PaymentProviderId.MOLLIE)

        mockMvc.perform(
            put(url).with(user(curatorId.toString()).roles("CURATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(body("asked by the association"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentProvider").value("MOLLIE"))
    }

    @Test
    fun `change - 422 without a reason`() {
        mockMvc.perform(
            put(url).with(user(curatorId.toString()).roles("CURATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(body("   "))
        ).andExpect(status().isUnprocessableEntity)

        verify(exactly = 0) { service.change(any(), any(), any(), any()) }
    }

    @Test
    fun `change - 422 with a reason over the maximum length`() {
        mockMvc.perform(
            put(url).with(user(curatorId.toString()).roles("CURATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("x".repeat(PaymentProviderChangeService.REASON_MAX_LENGTH + 1)))
        ).andExpect(status().isUnprocessableEntity)

        verify(exactly = 0) { service.change(any(), any(), any(), any()) }
    }

    @Test
    fun `change - 404 for an unknown association`() {
        every { service.change(associationId, any(), any(), any()) } throws NotFoundException("Association not found")

        mockMvc.perform(
            put(url).with(user(curatorId.toString()).roles("CURATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(body("asked by the association"))
        ).andExpect(status().isNotFound)
    }
}

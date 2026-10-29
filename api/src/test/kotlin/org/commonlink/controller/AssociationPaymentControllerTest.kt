package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.dto.PaymentStatusDto
import org.commonlink.exception.UserNotFoundException
import org.commonlink.payment.BankSetupStatus
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AssociationPaymentService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(AssociationPaymentController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AssociationPaymentControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var associationPaymentService: AssociationPaymentService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val url = "/api/association/payment/status"

    @Test
    fun `200 for an association, with its provider and status`() {
        every { associationPaymentService.getPaymentStatus(userId) } returns PaymentStatusDto(
            provider = PaymentProviderId.MOLLIE,
            status = BankSetupStatus.NEEDS_DATA,
            actionUrl = "https://my.mollie.com/dashboard/onboarding",
            warnings = emptyList(),
        )

        mockMvc.perform(get(url).with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.provider").value("MOLLIE"))
            .andExpect(jsonPath("$.status").value("NEEDS_DATA"))
            .andExpect(jsonPath("$.actionUrl").value("https://my.mollie.com/dashboard/onboarding"))
            .andExpect(jsonPath("$.warnings").isEmpty)
    }

    @Test
    fun `404 when the user has no association profile`() {
        every { associationPaymentService.getPaymentStatus(userId) } throws
            UserNotFoundException("Association profile not found for user $userId")

        mockMvc.perform(get(url).with(user(userId.toString()).roles("ASSOCIATION")))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `403 for a donor`() {
        mockMvc.perform(get(url).with(user(userId.toString()).roles("DONOR")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `401 without JWT`() {
        mockMvc.perform(get(url))
            .andExpect(status().isUnauthorized)
    }
}

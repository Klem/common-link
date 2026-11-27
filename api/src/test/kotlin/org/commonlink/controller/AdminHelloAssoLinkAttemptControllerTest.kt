package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.HelloAssoLinkAttemptOutcomeCount
import org.commonlink.dto.HelloAssoLinkAttemptPageResponse
import org.commonlink.dto.HelloAssoLinkAttemptStatsResponse
import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.HelloAssoLinkAttemptStatsService
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
import java.time.Instant

/** [AdminHelloAssoLinkAttemptController]: CURATOR / ADMIN only, parameters passed through. */
@WebMvcTest(AdminHelloAssoLinkAttemptController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AdminHelloAssoLinkAttemptControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var statsService: HelloAssoLinkAttemptStatsService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val from = Instant.parse("2026-11-01T00:00:00Z")
    private val to = Instant.parse("2026-12-01T00:00:00Z")

    private fun stubStats() {
        every { statsService.stats(any(), any()) } returns HelloAssoLinkAttemptStatsResponse(
            from, to, total = 3, associations = 2,
            byOutcome = listOf(
                HelloAssoLinkAttemptOutcomeCount(HelloAssoLinkAttemptOutcome.MATCHED, 1),
                HelloAssoLinkAttemptOutcomeCount(HelloAssoLinkAttemptOutcome.NAME_MISMATCH, 2),
            ),
        )
    }

    @Test
    fun `stats - 401 without JWT`() {
        mockMvc.perform(get("/api/admin/helloasso/link-attempts/stats")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `stats and list - 403 for ASSOCIATION and DONOR roles`() {
        listOf("ASSOCIATION", "DONOR").forEach { role ->
            mockMvc.perform(get("/api/admin/helloasso/link-attempts/stats").with(user("u").roles(role)))
                .andExpect(status().isForbidden)
            mockMvc.perform(get("/api/admin/helloasso/link-attempts").with(user("u").roles(role)))
                .andExpect(status().isForbidden)
        }
    }

    @Test
    fun `stats - CURATOR gets the counts for the given period`() {
        stubStats()

        mockMvc.perform(
            get("/api/admin/helloasso/link-attempts/stats")
                .param("from", "2026-11-01T00:00:00Z").param("to", "2026-12-01T00:00:00Z")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.byOutcome[1].outcome").value("NAME_MISMATCH"))

        verify { statsService.stats(from, to) }
    }

    @Test
    fun `list - ADMIN passes the outcome filter and paging`() {
        every { statsService.list(null, null, HelloAssoLinkAttemptOutcome.RNA_MISMATCH, 1, 50) } returns
            HelloAssoLinkAttemptPageResponse(emptyList(), 1, 50, 0, 0)

        mockMvc.perform(
            get("/api/admin/helloasso/link-attempts")
                .param("outcome", "RNA_MISMATCH").param("page", "1").param("size", "50")
                .with(user("admin").roles("ADMIN"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(1))
    }
}

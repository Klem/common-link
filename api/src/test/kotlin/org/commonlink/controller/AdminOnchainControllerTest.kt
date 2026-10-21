package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.OnchainOutboxService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(AdminOnchainController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class AdminOnchainControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var outbox: OnchainOutboxService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val jobId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    @Test
    fun `job - 401 without JWT`() {
        mockMvc.perform(get("/api/admin/onchain/jobs/$jobId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `job - 403 for DONOR role`() {
        mockMvc.perform(
            get("/api/admin/onchain/jobs/$jobId").with(user("donor-user").roles("DONOR"))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `job - 200 returns the job status`() {
        every { outbox.find(jobId) } returns OnchainJob(
            id = jobId,
            action = OnchainJobAction.RECORD_DONATION,
            payloadJson = "{}",
            txHash = "0xabc",
            blockNumber = 7,
        )

        mockMvc.perform(
            get("/api/admin/onchain/jobs/$jobId").with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.action").value("RECORD_DONATION"))
            .andExpect(jsonPath("$.txHash").value("0xabc"))
    }

    @Test
    fun `job - 404 when unknown`() {
        every { outbox.find(jobId) } returns null

        mockMvc.perform(
            get("/api/admin/onchain/jobs/$jobId").with(user("curator").roles("CURATOR"))
        ).andExpect(status().isNotFound)
    }

    /** Registry v2: campaign statuses are never recorded on-chain, so the moderation endpoint is gone. */
    @Test
    fun `campaign moderation endpoint no longer exists`() {
        mockMvc.perform(
            post("/api/admin/onchain/campaigns/${UUID.randomUUID()}/pause").with(user("curator").roles("CURATOR"))
        ).andExpect { result -> check(result.response.status in setOf(403, 404, 405)) { "unexpected ${result.response.status}" } }
    }

    /** No data recovery tooling: neither the history backfill nor the address re-derivation is exposed. */
    @Test
    fun `backfill and rederive endpoints no longer exist`() {
        listOf("/api/admin/onchain/backfill", "/api/admin/onchain/rederive-addresses").forEach { path ->
            mockMvc.perform(post(path).with(user("admin").roles("ADMIN")))
                .andExpect { result -> check(result.response.status in setOf(403, 404, 405)) { "$path: unexpected ${result.response.status}" } }
        }
    }
}

package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.repository.MoneriumConnectionRepository
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AssociationService
import org.commonlink.service.CampaignService
import org.commonlink.service.OnchainOutboxService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
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

    @MockkBean private lateinit var associationService: AssociationService
    @MockkBean private lateinit var campaignService: CampaignService
    @MockkBean private lateinit var outbox: OnchainOutboxService
    @MockkBean private lateinit var moneriumConnectionRepo: MoneriumConnectionRepository
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val assocId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val jobId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private fun pendingJob(action: OnchainJobAction) = OnchainJob(
        id = jobId,
        action = action,
        payloadJson = "{}",
    )

    // ── Security ──────────────────────────────────────────────────────────────

    @Test
    fun `association action - 401 without JWT`() {
        mockMvc.perform(post("/api/admin/onchain/associations/$assocId/verify"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `association action - 403 for DONOR role`() {
        mockMvc.perform(
            post("/api/admin/onchain/associations/$assocId/verify")
                .with(user("donor-user").roles("DONOR"))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `association action - 403 for ASSOCIATION role`() {
        mockMvc.perform(
            post("/api/admin/onchain/associations/$assocId/verify")
                .with(user("assoc-user").roles("ASSOCIATION"))
        ).andExpect(status().isForbidden)
    }

    // ── Association — happy path and error cases ───────────────────────────────

    @Test
    fun `association verify - 404 when association not found`() {
        every { associationService.existsById(assocId) } returns false

        mockMvc.perform(
            post("/api/admin/onchain/associations/$assocId/verify")
                .with(user("curator").roles("CURATOR"))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `association verify - 422 when no Monerium wallet`() {
        every { associationService.existsById(assocId) } returns true
        every { moneriumConnectionRepo.findByAssociationId(assocId) } returns null

        mockMvc.perform(
            post("/api/admin/onchain/associations/$assocId/verify")
                .with(user("curator").roles("CURATOR"))
        ).andExpect(status().`is`(422))
    }

    @Test
    fun `association verify - 202 happy path`() {
        every { associationService.existsById(assocId) } returns true
        every { moneriumConnectionRepo.findByAssociationId(assocId)?.walletAddress } returns null
        // Build a mock connection with a walletAddress
        val mockConn = io.mockk.mockk<org.commonlink.entity.MoneriumConnection>()
        every { mockConn.walletAddress } returns "0xDeAdBeEf00000000000000000000000000000001"
        every { moneriumConnectionRepo.findByAssociationId(assocId) } returns mockConn
        every { associationService.getIdentifier(assocId) } returns "123456789"
        every { outbox.enqueue(any(), any(), any()) } returns pendingJob(OnchainJobAction.VERIFY_ASSOCIATION)

        mockMvc.perform(
            post("/api/admin/onchain/associations/$assocId/verify")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.status").value(OnchainJobStatus.PENDING.name))
    }

    // ── Campaign — happy path and REVERT_TO_DRAFT ─────────────────────────────

    @Test
    fun `campaign pause - 404 when not found`() {
        every { campaignService.existsById(campaignId) } returns false

        mockMvc.perform(
            post("/api/admin/onchain/campaigns/$campaignId/pause")
                .with(user("curator").roles("CURATOR"))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `campaign pause - 202 happy path`() {
        every { campaignService.existsById(campaignId) } returns true
        justRun { campaignService.adminTransition(campaignId, any()) }
        every { outbox.enqueue(any(), any(), null) } returns pendingJob(OnchainJobAction.PAUSE_CAMPAIGN)

        mockMvc.perform(
            post("/api/admin/onchain/campaigns/$campaignId/pause")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
    }

    @Test
    fun `campaign revert_to_draft - 422 when not in REVERT_REQUESTED state`() {
        every { campaignService.existsById(campaignId) } returns true
        every { campaignService.adminTransition(campaignId, any()) } throws
            org.commonlink.exception.UnprocessableEntityException("Campaign must be in REVERT_REQUESTED state")

        mockMvc.perform(
            post("/api/admin/onchain/campaigns/$campaignId/revert_to_draft")
                .with(user("curator").roles("CURATOR"))
        ).andExpect(status().`is`(422))
    }

    @Test
    fun `campaign revert_to_draft - 422 when raised gt 0`() {
        every { campaignService.existsById(campaignId) } returns true
        every { campaignService.adminTransition(campaignId, any()) } throws
            org.commonlink.exception.UnprocessableEntityException("Cannot revert: raised > 0")

        mockMvc.perform(
            post("/api/admin/onchain/campaigns/$campaignId/revert_to_draft")
                .with(user("curator").roles("CURATOR"))
        ).andExpect(status().`is`(422))
    }

    @Test
    fun `campaign revert_to_draft - 202 uses idempotent correlation key`() {
        every { campaignService.existsById(campaignId) } returns true
        justRun { campaignService.adminTransition(campaignId, any()) }
        every { outbox.enqueue(any(), any(), "REVERT_CAMPAIGN_TO_DRAFT:$campaignId") } returns
            pendingJob(OnchainJobAction.REVERT_CAMPAIGN_TO_DRAFT)

        mockMvc.perform(
            post("/api/admin/onchain/campaigns/$campaignId/revert_to_draft")
                .with(user("curator").roles("CURATOR"))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
    }
}

package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.OnchainControlEntryDto
import org.commonlink.dto.OnchainFailedJobDto
import org.commonlink.dto.OnchainJobFeedDto
import org.commonlink.dto.OnchainJobFeedItemDto
import org.commonlink.dto.OnchainOutboxHealthDto
import org.commonlink.dto.OnchainPauseResultDto
import org.commonlink.dto.OnchainRegistryStatusDto
import org.commonlink.dto.OnchainUnpauseResultDto
import org.commonlink.exception.ConflictException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.ComplianceAuditLogService
import org.commonlink.service.OnchainOutboxReportService
import org.commonlink.service.OnchainPauseService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

/** HTTP contract of the compliance officer's freeze / resume of the on-chain registry and of the outbox view. */
@WebMvcTest(ComplianceOnchainController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class ComplianceOnchainControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var service: OnchainPauseService
    @MockkBean private lateinit var outboxReport: OnchainOutboxReportService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val officerId = UUID.fromString("00000000-0000-0000-0000-0000000000c0")
    private fun officer() = user(officerId.toString()).roles("COMPLIANCE_OFFICER")
    private fun pause(body: String) = post("/api/compliance/onchain/pause").contentType(MediaType.APPLICATION_JSON).content(body)
    private fun unpause(body: String) = post("/api/compliance/onchain/unpause").contentType(MediaType.APPLICATION_JSON).content(body)

    @Test
    fun `pause - 401 without JWT`() {
        mockMvc.perform(pause("""{"rationale":"x"}""")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `pause - 403 for curator and admin roles`() {
        listOf("CURATOR", "ADMIN", "DONOR").forEach { role ->
            mockMvc.perform(pause("""{"rationale":"x"}""").with(user(officerId.toString()).roles(role)))
                .andExpect(status().isForbidden)
        }
    }

    @Test
    fun `pause - 200 returns the transaction and passes the officer and rationale`() {
        every { service.pause(officerId, "Clé recorder compromise") } returns
            OnchainPauseResultDto(paused = true, alreadyPaused = false, txHash = "0xabc", blockNumber = 12,
                explorerTxUrl = "https://gnosis-chiado.blockscout.com/tx/0xabc")

        mockMvc.perform(pause("""{"rationale":"Clé recorder compromise"}""").with(officer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paused").value(true))
            .andExpect(jsonPath("$.alreadyPaused").value(false))
            .andExpect(jsonPath("$.txHash").value("0xabc"))
            .andExpect(jsonPath("$.blockNumber").value(12))
            .andExpect(jsonPath("$.explorerTxUrl").value("https://gnosis-chiado.blockscout.com/tx/0xabc"))
        verify(exactly = 1) { service.pause(officerId, "Clé recorder compromise") }
    }

    @Test
    fun `pause - 422 on a blank rationale, service never called`() {
        mockMvc.perform(pause("""{"rationale":"  "}""").with(officer())).andExpect(status().isUnprocessableContent)
        mockMvc.perform(pause("""{}""").with(officer())).andExpect { r -> check(r.response.status in setOf(400, 422)) }
        verify(exactly = 0) { service.pause(any(), any()) }
    }

    @Test
    fun `pause - 422 on a rationale longer than 1000 characters`() {
        mockMvc.perform(pause("""{"rationale":"${"x".repeat(1001)}"}""").with(officer()))
            .andExpect(status().isUnprocessableContent)
        verify(exactly = 0) { service.pause(any(), any()) }
    }

    @Test
    fun `pause - 409 in simulated mode`() {
        every { service.pause(officerId, "motif") } throws ConflictException("Mode simulé")
        mockMvc.perform(pause("""{"rationale":"motif"}""").with(officer())).andExpect(status().isConflict)
    }

    @Test
    fun `status - 200 for the compliance officer`() {
        every { service.status() } returns OnchainRegistryStatusDto(mock = false, chainId = 10200,
            registryAddress = "0x5FbDB2315678afecb367f032d93F642f64180aa3", paused = null, canUnpause = false,
            explorerAddressUrl = null)

        mockMvc.perform(get("/api/compliance/onchain/status").with(officer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mock").value(false))
            .andExpect(jsonPath("$.chainId").value(10200))
            .andExpect(jsonPath("$.paused").isEmpty)
            .andExpect(jsonPath("$.canUnpause").value(false))
    }

    @Test
    fun `unpause - 401 without JWT, 403 for other roles`() {
        mockMvc.perform(unpause("""{"rationale":"x"}""")).andExpect(status().isUnauthorized)
        listOf("CURATOR", "ADMIN", "DONOR", "ASSOCIATION").forEach { role ->
            mockMvc.perform(unpause("""{"rationale":"x"}""").with(user(officerId.toString()).roles(role)))
                .andExpect(status().isForbidden)
        }
        verify(exactly = 0) { service.unpause(any(), any()) }
    }

    @Test
    fun `unpause - 200 returns the transaction and passes the officer and rationale`() {
        every { service.unpause(officerId, "Incident clos") } returns
            OnchainUnpauseResultDto(paused = false, alreadyActive = false, txHash = "0xdef", blockNumber = 13,
                explorerTxUrl = null)

        mockMvc.perform(unpause("""{"rationale":"Incident clos"}""").with(officer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paused").value(false))
            .andExpect(jsonPath("$.alreadyActive").value(false))
            .andExpect(jsonPath("$.txHash").value("0xdef"))
        verify(exactly = 1) { service.unpause(officerId, "Incident clos") }
    }

    @Test
    fun `unpause - 422 on a blank or too long rationale, service never called`() {
        mockMvc.perform(unpause("""{"rationale":" "}""").with(officer())).andExpect(status().isUnprocessableContent)
        mockMvc.perform(unpause("""{"rationale":"${"x".repeat(1001)}"}""").with(officer()))
            .andExpect(status().isUnprocessableContent)
        verify(exactly = 0) { service.unpause(any(), any()) }
    }

    @Test
    fun `unpause - 409 when the platform lost the resume right`() {
        every { service.unpause(officerId, "motif") } throws ConflictException("UNPAUSER_ROLE révoqué")
        mockMvc.perform(unpause("""{"rationale":"motif"}""").with(officer())).andExpect(status().isConflict)
    }

    @Test
    fun `read-only views - 401 without JWT, 403 for other roles`() {
        listOf("/history", "/outbox", "/failed-jobs", "/jobs").forEach { path ->
            mockMvc.perform(get("/api/compliance/onchain$path")).andExpect(status().isUnauthorized)
            listOf("CURATOR", "ADMIN").forEach { role ->
                mockMvc.perform(get("/api/compliance/onchain$path").with(user(officerId.toString()).roles(role)))
                    .andExpect(status().isForbidden)
            }
        }
    }

    @Test
    fun `history - 200 returns journal entries with their raw payload`() {
        every { service.history() } returns listOf(
            OnchainControlEntryDto(sequenceNo = 250, eventType = ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSED,
                actorUserId = officerId, actorEmail = "compliance@example.org",
                payload = """{"rationale":"Gel","alreadyPaused":true}""", occurredAt = Instant.parse("2026-10-27T10:36:01Z")),
        )
        mockMvc.perform(get("/api/compliance/onchain/history").with(officer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].eventType").value("ONCHAIN_REGISTRY_PAUSED"))
            .andExpect(jsonPath("$[0].payload.rationale").value("Gel"))
            .andExpect(jsonPath("$[0].actorUserId").value(officerId.toString()))
            .andExpect(jsonPath("$[0].actorEmail").value("compliance@example.org"))
    }

    @Test
    fun `jobs - 200 returns the live feed`() {
        every { outboxReport.recentJobs() } returns OnchainJobFeedDto(chainId = 0, simulated = true, items = listOf(
            OnchainJobFeedItemDto(id = officerId, action = "RECORD_DONATION", status = "DONE", attempts = 1,
                txHash = "0xabc", blockNumber = 3, explorerTxUrl = null, error = null,
                createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        ))
        mockMvc.perform(get("/api/compliance/onchain/jobs").with(officer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.simulated").value(true))
            .andExpect(jsonPath("$.items[0].status").value("DONE"))
            .andExpect(jsonPath("$.items[0].txHash").value("0xabc"))
    }

    @Test
    fun `outbox and failed-jobs - 200 for the compliance officer`() {
        every { outboxReport.health() } returns OnchainOutboxHealthDto(chainId = 31337, pending = 4, running = 0,
            failed = 1, blockedByFailedDependency = 2, oldestPendingSince = Instant.parse("2026-10-27T10:33:23Z"))
        every { outboxReport.failedJobs() } returns listOf(OnchainFailedJobDto(id = officerId, action = "RECORD_DONATION",
            attempts = 5, error = "Failed to connect to <url>", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))

        mockMvc.perform(get("/api/compliance/onchain/outbox").with(officer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pending").value(4))
            .andExpect(jsonPath("$.blockedByFailedDependency").value(2))
        mockMvc.perform(get("/api/compliance/onchain/failed-jobs").with(officer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].action").value("RECORD_DONATION"))
            .andExpect(jsonPath("$[0].error").value("Failed to connect to <url>"))
    }
}

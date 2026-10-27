package org.commonlink.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.dto.OnchainPauseResultDto
import org.commonlink.dto.OnchainRegistryStatusDto
import org.commonlink.exception.ConflictException
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
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
import java.util.UUID

/** HTTP contract of the compliance officer's emergency freeze of the on-chain registry. */
@WebMvcTest(ComplianceOnchainController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!"
])
class ComplianceOnchainControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var service: OnchainPauseService
    @MockkBean private lateinit var jwtService: JwtService
    @MockkBean private lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean private lateinit var userRepository: UserRepository

    private val officerId = UUID.fromString("00000000-0000-0000-0000-0000000000c0")
    private fun officer() = user(officerId.toString()).roles("COMPLIANCE_OFFICER")
    private fun pause(body: String) = post("/api/compliance/onchain/pause").contentType(MediaType.APPLICATION_JSON).content(body)

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
    fun `pause - 409 in simulated mode`() {
        every { service.pause(officerId, "motif") } throws ConflictException("Mode simulé")
        mockMvc.perform(pause("""{"rationale":"motif"}""").with(officer())).andExpect(status().isConflict)
    }

    @Test
    fun `status - 200 for the compliance officer`() {
        every { service.status() } returns OnchainRegistryStatusDto(mock = false, chainId = 10200,
            registryAddress = "0x5FbDB2315678afecb367f032d93F642f64180aa3", paused = null, explorerAddressUrl = null)

        mockMvc.perform(get("/api/compliance/onchain/status").with(officer()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mock").value(false))
            .andExpect(jsonPath("$.chainId").value(10200))
            .andExpect(jsonPath("$.paused").isEmpty)
    }

    @Test
    fun `there is no resume endpoint`() {
        mockMvc.perform(post("/api/compliance/onchain/unpause").with(officer()))
            .andExpect { r -> check(r.response.status in setOf(404, 405)) { "unexpected ${r.response.status}" } }
    }
}

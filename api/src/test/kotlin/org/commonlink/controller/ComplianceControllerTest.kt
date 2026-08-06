package org.commonlink.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import com.ninjasquad.springmockk.MockkBean
import org.commonlink.security.ComplianceAccessLogFilter
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(ComplianceController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = [
    "springdoc.api-docs.enabled=false",
    "app.frontend-url=http://localhost:3000",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
])
class ComplianceControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private lateinit var listAppender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUpLogCapture() {
        val logger = LoggerFactory.getLogger(ComplianceAccessLogFilter::class.java) as Logger
        logger.level = Level.INFO
        listAppender = ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    @AfterEach
    fun tearDownLogCapture() {
        val logger = LoggerFactory.getLogger(ComplianceAccessLogFilter::class.java) as Logger
        logger.detachAppender(listAppender)
    }

    // ── Authorization — COMPLIANCE_OFFICER granted ──────────────────────────

    @Test
    fun `ping - 200 for COMPLIANCE_OFFICER`() {
        mockMvc.perform(
            get("/api/compliance/ping")
                .with(user(userId.toString()).roles("COMPLIANCE_OFFICER"))
        )
            .andExpect(status().isOk)
    }

    // ── Authorization — other roles denied ──────────────────────────────────

    @Test
    fun `ping - 403 for ASSOCIATION`() {
        mockMvc.perform(
            get("/api/compliance/ping")
                .with(user(userId.toString()).roles("ASSOCIATION"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `ping - 403 for DONOR`() {
        mockMvc.perform(
            get("/api/compliance/ping")
                .with(user(userId.toString()).roles("DONOR"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `ping - 403 for CURATOR`() {
        mockMvc.perform(
            get("/api/compliance/ping")
                .with(user(userId.toString()).roles("CURATOR"))
        )
            .andExpect(status().isForbidden)
    }

    // ── Audit log ────────────────────────────────────────────────────────────

    @Test
    fun `ping - logs exactly one INFO with caller id and route for COMPLIANCE_OFFICER`() {
        mockMvc.perform(
            get("/api/compliance/ping")
                .with(user(userId.toString()).roles("COMPLIANCE_OFFICER"))
        )
            .andExpect(status().isOk)

        val events = listAppender.list.filter { it.level == Level.INFO }
        assertThat(events).hasSize(1)
        assertThat(events[0].formattedMessage)
            .contains(userId.toString())
            .contains("/api/compliance/ping")
    }

    // ── Isolation — compliance role cannot reach other spaces ────────────────

    @Test
    fun `admin verifications - 403 for COMPLIANCE_OFFICER`() {
        mockMvc.perform(
            get("/api/admin/verifications/")
                .with(user(userId.toString()).roles("COMPLIANCE_OFFICER"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `ping - 403 for ADMIN`() {
        mockMvc.perform(
            get("/api/compliance/ping")
                .with(user(userId.toString()).roles("ADMIN"))
        )
            .andExpect(status().isForbidden)
    }
}

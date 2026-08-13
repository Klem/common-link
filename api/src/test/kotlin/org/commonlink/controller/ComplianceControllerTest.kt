package org.commonlink.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import com.ninjasquad.springmockk.MockkBean
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import java.time.Instant
import io.mockk.every
import org.commonlink.dto.RegistryPreCheckDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ScopeVerdict
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.commonlink.repository.UserRepository
import org.commonlink.security.ComplianceAccessLogFilter
import org.commonlink.security.JwtAuthenticationFilter
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AssociationRegistryCheckService
import org.commonlink.service.ComplianceAlertService
import org.commonlink.service.ComplianceAuditLogService
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
import java.util.Optional
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

    @MockkBean
    private lateinit var alertService: ComplianceAlertService

    @MockkBean
    private lateinit var auditLogService: ComplianceAuditLogService

    @MockkBean
    private lateinit var registryCheckService: AssociationRegistryCheckService

    @MockkBean
    private lateinit var registryCheckRepository: AssociationRegistryCheckRepository

    @MockkBean
    private lateinit var associationProfileRepository: AssociationProfileRepository

    @MockkBean
    private lateinit var beneficialOwnerRepository: BeneficialOwnerRepository

    @MockkBean
    private lateinit var donorProfileRepository: DonorProfileRepository

    @MockkBean
    private lateinit var userRepository: UserRepository

    @MockkBean
    private lateinit var matchRepository: FreezeScreeningMatchRepository

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

    // ── Registry scans ───────────────────────────────────────────────────────

    @Test
    fun `registry-scans - 200 for COMPLIANCE_OFFICER`() {
        every { registryCheckRepository.findAssociationIdsWithScansOrderedByLatest() } returns emptyList()
        mockMvc.perform(
            get("/api/compliance/registry-scans")
                .with(user(userId.toString()).roles("COMPLIANCE_OFFICER"))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `registry-scans - 403 for CURATOR`() {
        mockMvc.perform(
            get("/api/compliance/registry-scans")
                .with(user(userId.toString()).roles("CURATOR"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `registry-scans - maps association name and warning count`() {
        val assocId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val profile = AssociationProfile(
            id = assocId,
            user = User(email = "test@example.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL),
            name = "Assoc Test",
            identifier = "775671356",
        )
        val dto = RegistryPreCheckDto(
            id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
            associationExists = true,
            siren = "123456789",
            rna = null,
            legalCategory = "9220",
            scopeVerdict = ScopeVerdict.IN_SCOPE,
            etatAdministratif = "A",
            joafeDeclarationFound = true,
            dissolutionDetected = false,
            bodaccProcedureFound = false,
            checkedAt = Instant.parse("2026-08-01T10:00:00Z"),
            warnings = listOf("source-x-timeout", "source-y-timeout"),
            officers = emptyList(),
            rnaActive = true,
        )
        every { registryCheckRepository.findAssociationIdsWithScansOrderedByLatest() } returns listOf(assocId)
        every { associationProfileRepository.findAllById(listOf(assocId)) } returns listOf(profile)
        every { registryCheckService.latest(assocId) } returns dto

        mockMvc.perform(
            get("/api/compliance/registry-scans")
                .with(user(userId.toString()).roles("COMPLIANCE_OFFICER"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].associationName").value("Assoc Test"))
            .andExpect(jsonPath("$.content[0].warningCount").value(2))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    // ── Alert detail — subject resolution ────────────────────────────────────

    /**
     * A freeze hit was observed in production on an association id absent from
     * `association_profiles`. The screening evidence and the alert survive their subject's
     * dossier, so the detail endpoint must degrade to nulls rather than fail — an officer
     * has to be able to open and close an alert whose dossier no longer exists.
     */
    @Test
    fun `alert detail - returns 200 with null subject label when the dossier no longer resolves`() {
        val danglingId = UUID.fromString("3b3d9ebd-0000-0000-0000-000000000000")
        val alertId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
        val alert = ComplianceAlert(
            id = alertId,
            origin = ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
            subjectType = ComplianceAlertSubjectType.ASSOCIATION,
            subjectId = danglingId,
            severity = ComplianceAlertSeverity.HIGH,
            createdAt = Instant.parse("2026-08-13T08:43:06Z"),
        )
        every { alertService.findById(alertId) } returns alert
        every { alertService.findPriorDecisions(danglingId, alertId) } returns emptyList()
        every { auditLogService.findFreezeScreeningHistory(danglingId) } returns emptyList()
        every { matchRepository.findByAssociationIdOrderByScoreDesc(danglingId) } returns emptyList()
        every { associationProfileRepository.findById(danglingId) } returns Optional.empty()
        every { registryCheckService.latest(danglingId) } returns null

        mockMvc.perform(
            get("/api/compliance/alerts/$alertId")
                .with(user(userId.toString()).roles("COMPLIANCE_OFFICER"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subjectLabel").doesNotExist())
            .andExpect(jsonPath("$.subjectRegistry").doesNotExist())
            .andExpect(jsonPath("$.subjectId").value(danglingId.toString()))
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

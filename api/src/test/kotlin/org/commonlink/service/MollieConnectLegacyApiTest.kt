package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for the LEGACY onboarding API path (`GET /v2/onboarding/me`).
 * Kept in a separate class because [onboarding-api=LEGACY] is a class-level property source
 * that would conflict with the [MollieConnectServiceTest] stubs which target `/v2/capabilities`.
 *
 * Covers [OnboardingMeResponse.toOnboardingSnapshot] transitively via the service layer,
 * since the response DTO is file-private.
 */
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
    "app.mollie.connect.client-id=test_client",
    "app.mollie.connect.client-secret=test_secret",
    "app.mollie.connect.advanced-token=test_advanced_token",
    "app.mollie.connect.redirect-uri=http://localhost:8080/api/public/webhooks/mollie-connect",
    "app.mollie.connect.scopes=onboarding.read",
    "app.mollie.connect.mock=false",
    "app.mollie.connect.allow-fake-completion=false",
    "app.mollie.connect.onboarding-api=LEGACY",
])
@Transactional
class MollieConnectLegacyApiTest {

    @Autowired private lateinit var mollieConnectService: MollieConnectService
    @Autowired private lateinit var restTemplate: RestTemplate
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var mollieConnectionRepository: MollieConnectionRepository

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var association: AssociationProfile
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate)
        val user = userRepository.save(
            TestFixtures.associationUser(email = "legacy-${System.nanoTime()}@example.com")
        )
        association = associationProfileRepository.save(TestFixtures.associationProfile(user))
        userId = user.id!!
    }

    @Test
    fun `legacy path - completed maps to COMPLETED with canReceivePayments`() {
        stubConnection()
        stubOnboardingMe(status = "completed", canReceivePayments = true, canReceiveSettlements = true)

        val dto = mollieConnectService.getConnectionStatus(userId)

        assertEquals("COMPLETED", dto.onboardingStatus)
        assertTrue(dto.canReceivePayments!!)
        assertNull(dto.dashboardUrl)
        mockServer.verify()
    }

    @Test
    fun `legacy path - in-review maps to IN_REVIEW`() {
        stubConnection()
        stubOnboardingMe(status = "in-review")

        val dto = mollieConnectService.getConnectionStatus(userId)

        assertEquals("IN_REVIEW", dto.onboardingStatus)
        mockServer.verify()
    }

    @Test
    fun `legacy path - needs-data maps to NEEDS_DATA with dashboardUrl`() {
        stubConnection()
        stubOnboardingMe(
            status = "needs-data",
            dashboardHref = "https://my.mollie.com/dashboard/org_test/onboarding",
        )

        val dto = mollieConnectService.getConnectionStatus(userId)

        assertEquals("NEEDS_DATA", dto.onboardingStatus)
        assertEquals("https://my.mollie.com/dashboard/org_test/onboarding", dto.dashboardUrl)
        mockServer.verify()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun stubConnection() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "valid_token",
            refreshToken = "ref",
            expiresAt = Instant.now().plusSeconds(3600),
            onboardingStatus = MollieOnboardingStatus.NEEDS_DATA,
            lastSyncedAt = Instant.now().minusSeconds(600),
        ))
    }

    private fun stubOnboardingMe(
        status: String,
        canReceivePayments: Boolean = false,
        canReceiveSettlements: Boolean = false,
        dashboardHref: String? = null,
    ) {
        val dashboardJson = if (dashboardHref != null)
            """"dashboard":{"href":"$dashboardHref","type":"text/html"}"""
        else
            """"dashboard":null"""
        mockServer.expect(requestTo("https://api.mollie.com/v2/onboarding/me"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """{
                  "resource":"onboarding",
                  "status":"$status",
                  "canReceivePayments":$canReceivePayments,
                  "canReceiveSettlements":$canReceiveSettlements,
                  "_links":{$dashboardJson}
                }""",
                MediaType.APPLICATION_JSON,
            ))
    }
}

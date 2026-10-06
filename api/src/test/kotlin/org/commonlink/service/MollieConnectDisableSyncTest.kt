package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for [MollieConnectService.getConnectionStatus] with the dev/staging escape
 * hatch ENABLED (app.mollie.connect.disable-onboarding-sync=true). Kept in a separate class
 * because the flag is a class-level property source that cannot be toggled per test — mirrors
 * [MollieConnectForceCompleteTest].
 *
 * No stubbed HTTP response is registered on the [MockRestServiceServer]: the point of the flag is
 * that no outbound call happens at all, so an unexpected request fails the test on the real
 * [MockRestServiceServer.verify] assertion rather than on a missing stub.
 */
@Tag("testcontainers")
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
    "app.mollie.connect.onboarding-api=CAPABILITIES",
    "app.mollie.connect.disable-onboarding-sync=true",
])
@Transactional
class MollieConnectDisableSyncTest {

    @Autowired private lateinit var mollieConnectService: MollieConnectService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var mollieConnectionRepository: MollieConnectionRepository
    @Autowired private lateinit var restTemplate: RestTemplate

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var association: AssociationProfile
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).build()
        val user = userRepository.save(
            TestFixtures.associationUser(email = "mollie-nosync-${System.nanoTime()}@example.com")
        )
        association = associationProfileRepository.save(TestFixtures.associationProfile(user))
        userId = user.id!!
    }

    @Test
    fun `getConnectionStatus - never calls Mollie and keeps the stale DB status when sync disabled`() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "valid_token",
            refreshToken = "ref",
            expiresAt = Instant.now().plusSeconds(3600),
            state = MollieConnectionState.ACTIVE,
            onboardingStatus = MollieOnboardingStatus.NEEDS_DATA,
            canReceivePayments = false,
            // Old enough that, without the flag, this would trigger a live resync.
            lastSyncedAt = Instant.now().minusSeconds(3600),
        ))

        val dto = mollieConnectService.getConnectionStatus(userId)

        assertEquals("NEEDS_DATA", dto.onboardingStatus)
        assertFalse(dto.canReceivePayments!!)
        mockServer.verify() // no outbound Mollie request was expected or made

        val persisted = mollieConnectionRepository.findByAssociationId(association.id!!)!!
        assertEquals(MollieOnboardingStatus.NEEDS_DATA, persisted.onboardingStatus)
    }
}

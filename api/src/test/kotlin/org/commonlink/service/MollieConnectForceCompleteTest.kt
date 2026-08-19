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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for [MollieConnectService.forceCompleteOnboarding] with the dev/staging
 * escape hatch ENABLED (app.mollie.connect.allow-fake-completion=true). Kept in a separate
 * class because the flag is a class-level property source that cannot be toggled per test.
 *
 * No [org.springframework.test.web.client.MockRestServiceServer] is needed: force-completion
 * never calls Mollie — it only mutates the existing connection row.
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
    "app.mollie.connect.allow-fake-completion=true",
])
@Transactional
class MollieConnectForceCompleteTest {

    @Autowired private lateinit var mollieConnectService: MollieConnectService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var mollieConnectionRepository: MollieConnectionRepository

    private lateinit var association: AssociationProfile
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(
            TestFixtures.associationUser(email = "mollie-fc-${System.nanoTime()}@example.com")
        )
        association = associationProfileRepository.save(TestFixtures.associationProfile(user))
        userId = user.id!!
    }

    @Test
    fun `forceCompleteOnboarding - flips existing NEEDS_DATA connection to COMPLETED`() {
        mollieConnectionRepository.save(MollieConnection(
            association = association,
            accessToken = "valid_token",
            refreshToken = "ref",
            expiresAt = Instant.now().plusSeconds(3600),
            onboardingStatus = MollieOnboardingStatus.NEEDS_DATA,
            canReceivePayments = false,
        ))

        val dto = mollieConnectService.forceCompleteOnboarding(userId)

        assertTrue(dto.connected)
        assertEquals("COMPLETED", dto.onboardingStatus)
        assertTrue(dto.canReceivePayments!!)
        assertTrue(dto.canForceComplete)

        val persisted = mollieConnectionRepository.findByAssociationId(association.id!!)!!
        assertEquals(MollieOnboardingStatus.COMPLETED, persisted.onboardingStatus)
        assertTrue(persisted.canReceivePayments)
        assertTrue(persisted.canReceiveSettlements)
    }

    @Test
    fun `forceCompleteOnboarding - throws when no connection exists`() {
        assertThrows<IllegalStateException> {
            mollieConnectService.forceCompleteOnboarding(userId)
        }
    }
}

package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOAuthState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieOAuthStateRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
 * Integration tests for [OnboardingGateService] — the server-side enforcement of the association
 * onboarding chain (mirror of the frontend tab lock). Verifies each guard's throw/pass boundary
 * against a real database.
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
])
@Transactional
class OnboardingGateServiceTest {

    @Autowired private lateinit var onboardingGate: OnboardingGateService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var fiscalMandateRepository: FiscalMandateRepository
    @Autowired private lateinit var mollieConnectionRepository: MollieConnectionRepository
    @Autowired private lateinit var mollieOAuthStateRepository: MollieOAuthStateRepository

    private lateinit var association: AssociationProfile
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(
            TestFixtures.associationUser(email = "gate-${System.nanoTime()}@example.com")
        )
        association = associationProfileRepository.save(TestFixtures.associationProfile(user))
        userId = user.id!!
    }

    private fun completedConnection(canReceivePayments: Boolean) = MollieConnection(
        association = association,
        accessToken = "acc",
        refreshToken = "ref",
        expiresAt = Instant.now().plusSeconds(3600),
        mollieOrganizationId = "org_${System.nanoTime()}",
        onboardingStatus = MollieOnboardingStatus.COMPLETED,
        canReceivePayments = canReceivePayments,
    )

    // ── requireMandateSigned ──────────────────────────────────────────────────

    @Test
    fun `requireMandateSigned - throws when no mandate exists`() {
        assertThrows<ConflictException> { onboardingGate.requireMandateSigned(userId) }
    }

    @Test
    fun `requireMandateSigned - throws when the only mandate is revoked`() {
        fiscalMandateRepository.save(
            TestFixtures.fiscalMandate(association).apply { revokedAt = Instant.now() }
        )
        assertThrows<ConflictException> { onboardingGate.requireMandateSigned(userId) }
    }

    @Test
    fun `requireMandateSigned - passes when an active mandate exists`() {
        fiscalMandateRepository.save(TestFixtures.fiscalMandate(association))
        assertDoesNotThrow { onboardingGate.requireMandateSigned(userId) }
    }

    @Test
    fun `requireMandateSigned - throws UserNotFoundException when profile is unknown`() {
        assertThrows<UserNotFoundException> { onboardingGate.requireMandateSigned(UUID.randomUUID()) }
    }

    // ── requireBankReady ──────────────────────────────────────────────────────

    @Test
    fun `requireBankReady - throws when no Mollie connection exists`() {
        assertThrows<ConflictException> { onboardingGate.requireBankReady(userId) }
    }

    @Test
    fun `requireBankReady - throws when connection cannot receive payments`() {
        mollieConnectionRepository.save(completedConnection(canReceivePayments = false))
        assertThrows<ConflictException> { onboardingGate.requireBankReady(userId) }
    }

    /**
     * Discriminating case: a widget cannot work on an unpublished campaign, and publication requires
     * completed Mollie KYC. The widget gate must therefore be at least as strict — IN_REVIEW is
     * refused even when Mollie already allows payments.
     */
    @Test
    fun `requireBankReady - throws when onboarding is still IN_REVIEW despite payments allowed`() {
        mollieConnectionRepository.save(
            completedConnection(canReceivePayments = true)
                .apply { onboardingStatus = MollieOnboardingStatus.IN_REVIEW }
        )
        assertThrows<ConflictException> { onboardingGate.requireBankReady(userId) }
    }

    @Test
    fun `requireBankReady - throws when the connection is BROKEN`() {
        mollieConnectionRepository.save(
            completedConnection(canReceivePayments = true)
                .apply { state = MollieConnectionState.BROKEN }
        )
        assertThrows<ConflictException> { onboardingGate.requireBankReady(userId) }
    }

    @Test
    fun `requireBankReady - passes when connected and can receive payments`() {
        mollieConnectionRepository.save(completedConnection(canReceivePayments = true))
        assertDoesNotThrow { onboardingGate.requireBankReady(userId) }
    }

    // ── isMollieKycStarted ────────────────────────────────────────────────────

    @Test
    fun `isMollieKycStarted - false when the association never launched the flow`() {
        assertFalse(onboardingGate.isMollieKycStarted(userId))
    }

    /**
     * Discriminating case for the contact-field lock: the client-link carrying contactName /
     * contactEmail is created *before* the OAuth callback, so a live OAuth state alone must lock
     * the fields — waiting for a MollieConnection row would leave an editable window.
     */
    @Test
    fun `isMollieKycStarted - true while an OAuth state is still alive and no connection exists`() {
        mollieOAuthStateRepository.save(
            MollieOAuthState(
                state = UUID.randomUUID().toString(),
                association = association,
                expiresAt = Instant.now().plusSeconds(600),
            )
        )
        assertTrue(onboardingGate.isMollieKycStarted(userId))
    }

    /** An abandoned flow releases the lock: the next attempt re-sends whatever contact data is current. */
    @Test
    fun `isMollieKycStarted - false when the OAuth state has expired`() {
        mollieOAuthStateRepository.save(
            MollieOAuthState(
                state = UUID.randomUUID().toString(),
                association = association,
                expiresAt = Instant.now().minusSeconds(60),
            )
        )
        assertFalse(onboardingGate.isMollieKycStarted(userId))
    }

    /** The lock must not wait for COMPLETED — NEEDS_DATA is already "started". */
    @Test
    fun `isMollieKycStarted - true when the connection exists but KYC is not completed`() {
        mollieConnectionRepository.save(
            completedConnection(canReceivePayments = false)
                .apply { onboardingStatus = MollieOnboardingStatus.NEEDS_DATA }
        )
        assertTrue(onboardingGate.isMollieKycStarted(userId))
    }
}

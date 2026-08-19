package org.commonlink.service

import org.commonlink.dto.SignMandateRequest
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MandateEligibility
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Integration tests for [MandateService.signMandate] — specifically the authorised-signer guard.
 *
 * The controller test mocks [MandateService], so the guard is only reachable at service level.
 * Mirrors the frontend warning modal in `MandateTab`: a mandate authorises CommonLink to issue tax
 * receipts bearing the signer's name and role, so neither may be blank at signature time.
 */
@Tag("testcontainers")
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@Transactional
class MandateServiceTest {

    @Autowired private lateinit var mandateService: MandateService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository

    private lateinit var association: AssociationProfile
    private lateinit var userId: UUID

    private val validRequest = SignMandateRequest(eligibility = MandateEligibility.OIG_66, accepted = true)

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(
            TestFixtures.associationUser(email = "mandate-${System.nanoTime()}@example.com")
        )
        association = associationProfileRepository.save(
            TestFixtures.associationProfile(user, verificationStatus = VerificationStatus.VERIFIED)
        )
        userId = user.id!!
    }

    private fun withSigner(name: String?, role: String?) {
        association.signerName = name
        association.signerRole = role
        associationProfileRepository.save(association)
    }

    @Test
    fun `signMandate - refused when the signer name is missing`() {
        withSigner(name = null, role = "Présidente")
        assertThrows<UnprocessableEntityException> { mandateService.signMandate(userId, validRequest) }
    }

    @Test
    fun `signMandate - refused when the signer role is missing`() {
        withSigner(name = "Marie Dupont", role = null)
        assertThrows<UnprocessableEntityException> { mandateService.signMandate(userId, validRequest) }
    }

    /** Blank is treated as absent — a whitespace-only value would print as an empty line on receipts. */
    @Test
    fun `signMandate - refused when the signer fields are blank`() {
        withSigner(name = "   ", role = "  ")
        assertThrows<UnprocessableEntityException> { mandateService.signMandate(userId, validRequest) }
    }

    // Pas de test "passe quand les deux champs sont remplis" : la suite tourne sur un schéma généré
    // par Hibernate (create-drop, Flyway désactivé), donc la séquence `fiscal_mandate_ref_seq` créée
    // par la migration V35 n'existe pas et `signMandate` échoue en aval de la garde, sur la génération
    // de la référence. Le chemin nominal reste couvert par MandateControllerTest (service mocké).
}

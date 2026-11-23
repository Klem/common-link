package org.commonlink.service

import org.commonlink.dto.SignMandateRequest
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MandateEligibility
import org.commonlink.entity.ReceiptPrerequisite
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationSignatureRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
 * Integration tests for [MandateService.signMandate] — specifically the authorised-signer and
 * receipt-prerequisite guards — and for the prerequisites exposed by [MandateService.getMandateState].
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
    @Autowired private lateinit var signatureRepository: AssociationSignatureRepository

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

    /**
     * Every receipt issued under the mandate carries the signature, the legal object and the full head-office address:
     * a new mandate needs all three first, and the 422 names each missing one.
     */
    @Test
    fun `signMandate - refused listing every missing receipt prerequisite`() {
        withSigner(name = "Marie Dupont", role = "Présidente")

        val ex = assertThrows<UnprocessableEntityException> { mandateService.signMandate(userId, validRequest) }

        assertTrue(ex.message!!.contains("SIGNATURE"), ex.message)
        assertTrue(ex.message!!.contains("LEGAL_OBJECT"), ex.message)
        assertTrue(ex.message!!.contains("HEAD_OFFICE_ADDRESS"), ex.message)
    }

    @Test
    fun `signMandate - refused when only the receipt signature is missing`() {
        withSigner(name = "Marie Dupont", role = "Présidente")
        associationProfileRepository.save(TestFixtures.withReceiptPrerequisiteFields(association))

        val ex = assertThrows<UnprocessableEntityException> { mandateService.signMandate(userId, validRequest) }

        assertEquals("Receipt prerequisites missing before signing the fiscal mandate: SIGNATURE", ex.message)
    }

    /** The address counts as missing when any of street, postal code or city is blank. */
    @Test
    fun `signMandate - refused when the head-office city is blank`() {
        withSigner(name = "Marie Dupont", role = "Présidente")
        TestFixtures.withReceiptPrerequisiteFields(association).city = " "
        associationProfileRepository.save(association)
        signatureRepository.save(TestFixtures.associationSignature(association.id!!))

        val ex = assertThrows<UnprocessableEntityException> { mandateService.signMandate(userId, validRequest) }

        assertEquals("Receipt prerequisites missing before signing the fiscal mandate: HEAD_OFFICE_ADDRESS", ex.message)
    }

    // ── getMandateState.receiptPrerequisitesMissing ───────────────────────────

    @Test
    fun `getMandateState - lists the missing receipt prerequisites while unsigned`() {
        association.addressLine1 = null
        associationProfileRepository.save(association)

        val state = mandateService.getMandateState(userId)

        assertEquals(false, state.signed)
        assertEquals(
            listOf(ReceiptPrerequisite.SIGNATURE, ReceiptPrerequisite.LEGAL_OBJECT, ReceiptPrerequisite.HEAD_OFFICE_ADDRESS),
            state.receiptPrerequisitesMissing,
        )
    }

    @Test
    fun `getMandateState - empty list once every receipt prerequisite is met`() {
        associationProfileRepository.save(TestFixtures.withReceiptPrerequisiteFields(association))
        signatureRepository.save(TestFixtures.associationSignature(association.id!!))

        assertEquals(emptyList<ReceiptPrerequisite>(), mandateService.getMandateState(userId).receiptPrerequisitesMissing)
    }

    // No "passes when both fields are filled" test: the suite runs on a Hibernate-generated schema
    // (create-drop, Flyway disabled), so the `fiscal_mandate_ref_seq` sequence created by migration V35
    // does not exist and `signMandate` fails downstream of the guards, on reference generation. The
    // nominal path stays covered by MandateControllerTest (mocked service).
}

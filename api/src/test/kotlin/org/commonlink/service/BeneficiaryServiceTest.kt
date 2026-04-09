package org.commonlink.service

import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Integration tests for [BeneficiaryService] using a real PostgreSQL container via Testcontainers.
 *
 * Each test runs in a transaction that is rolled back after completion, so the database is
 * always in a clean state between tests.
 */
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@TestPropertySource(properties = [
    "spring.profiles.active=local",
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true"
])
@Transactional
class BeneficiaryServiceTest {

    @Autowired
    private lateinit var beneficiaryService: BeneficiaryService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var associationProfileRepository: AssociationProfileRepository

    private lateinit var userId: UUID

    /** Valid French IBAN (DE89, ends in '0' → MATCH in demo VOP). */
    private val validIban = "DE89370400440532013000"

    /** Syntactically invalid IBAN that fails mod-97 check. */
    private val invalidIban = "NOTANIBAN123"

    @BeforeEach
    fun setUpAssociation() {
        val user = userRepository.save(TestFixtures.associationUser())
        associationProfileRepository.save(TestFixtures.associationProfile(user))
        userId = user.id!!
    }

    // ── listBeneficiaries ─────────────────────────────────────────────────────

    @Test
    fun `listBeneficiaries - returns empty list for new association`() {
        val result = beneficiaryService.listBeneficiaries(userId)

        assertTrue(result.isEmpty())
    }

    // ── createBeneficiary ─────────────────────────────────────────────────────

    @Test
    fun `createBeneficiary - creates and returns beneficiary with empty ibans`() {
        val request = org.commonlink.dto.CreateBeneficiaryRequest(
            name = "Les Restos du Coeur",
            identifier1 = "775671356",
            city = "Paris"
        )

        val result = beneficiaryService.createBeneficiary(userId, request)

        assertNotNull(result.id)
        assertEquals("Les Restos du Coeur", result.name)
        assertEquals("775671356", result.identifier1)
        assertEquals("Paris", result.city)
        assertTrue(result.ibans.isEmpty())
    }

    @Test
    fun `createBeneficiary - throws ConflictException on duplicate SIREN`() {
        val request = org.commonlink.dto.CreateBeneficiaryRequest(
            name = "Les Restos du Coeur",
            identifier1 = "775671356"
        )
        beneficiaryService.createBeneficiary(userId, request)

        assertThrows<ConflictException> {
            beneficiaryService.createBeneficiary(userId, request.copy(name = "Duplicate Name"))
        }
    }

    // ── addIban ───────────────────────────────────────────────────────────────

    @Test
    fun `addIban - valid IBAN gets FORMAT_VALID status`() {
        val beneficiary = beneficiaryService.createBeneficiary(
            userId,
            org.commonlink.dto.CreateBeneficiaryRequest(name = "Test Org", identifier1 = "775671356")
        )

        val result = beneficiaryService.addIban(
            userId,
            beneficiary.id,
            org.commonlink.dto.AddIbanRequest(iban = validIban)
        )

        assertEquals(1, result.ibans.size)
        assertEquals(validIban, result.ibans[0].iban)
        assertEquals(IbanVerificationStatus.FORMAT_VALID, result.ibans[0].status)
    }

    @Test
    fun `addIban - invalid IBAN gets INVALID status`() {
        val beneficiary = beneficiaryService.createBeneficiary(
            userId,
            org.commonlink.dto.CreateBeneficiaryRequest(name = "Test Org", identifier1 = "775671356")
        )

        val result = beneficiaryService.addIban(
            userId,
            beneficiary.id,
            org.commonlink.dto.AddIbanRequest(iban = invalidIban)
        )

        assertEquals(1, result.ibans.size)
        assertEquals(IbanVerificationStatus.INVALID, result.ibans[0].status)
    }

    // ── deleteBeneficiary ─────────────────────────────────────────────────────

    @Test
    fun `deleteBeneficiary - removes the beneficiary`() {
        val beneficiary = beneficiaryService.createBeneficiary(
            userId,
            org.commonlink.dto.CreateBeneficiaryRequest(name = "Test Org", identifier1 = "775671356")
        )

        beneficiaryService.deleteBeneficiary(userId, beneficiary.id)

        val remaining = beneficiaryService.listBeneficiaries(userId)
        assertTrue(remaining.isEmpty())
    }

    // ── deleteIban ────────────────────────────────────────────────────────────

    @Test
    fun `deleteIban - removes the IBAN`() {
        val beneficiary = beneficiaryService.createBeneficiary(
            userId,
            org.commonlink.dto.CreateBeneficiaryRequest(name = "Test Org", identifier1 = "775671356")
        )
        val withIban = beneficiaryService.addIban(
            userId,
            beneficiary.id,
            org.commonlink.dto.AddIbanRequest(iban = validIban)
        )
        val ibanId = withIban.ibans[0].id

        beneficiaryService.deleteIban(userId, beneficiary.id, ibanId)

        val updated = beneficiaryService.listBeneficiaries(userId)
        assertTrue(updated[0].ibans.isEmpty())
    }

    // ── verifyIbanVop ─────────────────────────────────────────────────────────

    @Test
    fun `verifyIbanVop - returns VOP result for FORMAT_VALID IBAN`() {
        val beneficiary = beneficiaryService.createBeneficiary(
            userId,
            org.commonlink.dto.CreateBeneficiaryRequest(name = "Test Org", identifier1 = "775671356")
        )
        val withIban = beneficiaryService.addIban(
            userId,
            beneficiary.id,
            org.commonlink.dto.AddIbanRequest(iban = validIban)
        )
        val ibanId = withIban.ibans[0].id

        val result = beneficiaryService.verifyIbanVop(userId, beneficiary.id, ibanId)

        assertNotNull(result.vopResult)
        assertNotNull(result.status)
        assertNotEquals(IbanVerificationStatus.FORMAT_VALID, result.status)
    }

    @Test
    fun `verifyIbanVop - throws UnprocessableEntityException for non FORMAT_VALID IBAN`() {
        val beneficiary = beneficiaryService.createBeneficiary(
            userId,
            org.commonlink.dto.CreateBeneficiaryRequest(name = "Test Org", identifier1 = "775671356")
        )
        val withIban = beneficiaryService.addIban(
            userId,
            beneficiary.id,
            org.commonlink.dto.AddIbanRequest(iban = invalidIban)
        )
        val ibanId = withIban.ibans[0].id

        assertThrows<UnprocessableEntityException> {
            beneficiaryService.verifyIbanVop(userId, beneficiary.id, ibanId)
        }
    }
}

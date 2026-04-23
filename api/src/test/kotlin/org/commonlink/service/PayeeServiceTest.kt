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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Integration tests for [PayeeService] using a real PostgreSQL container via Testcontainers.
 *
 * Each test runs in a transaction that is rolled back after completion, so the database is
 * always in a clean state between tests.
 */
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.vop.demo-mode=true"])
@Transactional
class PayeeServiceTest {

    @Autowired
    private lateinit var payeeService: PayeeService

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

    // ── listPayees ─────────────────────────────────────────────────────────────

    @Test
    fun `listPayees - returns empty list for new association`() {
        val result = payeeService.listPayees(userId)

        assertTrue(result.isEmpty())
    }

    // ── createPayee ────────────────────────────────────────────────────────────

    @Test
    fun `createPayee - creates and returns payee with empty ibans`() {
        val request = org.commonlink.dto.CreatePayeeRequest(
            name = "Les Restos du Coeur",
            identifier1 = "775671356",
            city = "Paris"
        )

        val result = payeeService.createPayee(userId, request)

        assertNotNull(result.id)
        assertEquals("Les Restos du Coeur", result.name)
        assertEquals("775671356", result.identifier1)
        assertEquals("Paris", result.city)
        assertTrue(result.ibans.isEmpty())
    }

    @Test
    fun `createPayee - throws ConflictException on duplicate SIREN`() {
        val request = org.commonlink.dto.CreatePayeeRequest(
            name = "Les Restos du Coeur",
            identifier1 = "775671356"
        )
        payeeService.createPayee(userId, request)

        assertThrows<ConflictException> {
            payeeService.createPayee(userId, request.copy(name = "Duplicate Name"))
        }
    }

    // ── addIban ───────────────────────────────────────────────────────────────

    @Test
    fun `addIban - valid IBAN gets FORMAT_VALID status`() {
        val payee = payeeService.createPayee(
            userId,
            org.commonlink.dto.CreatePayeeRequest(name = "Test Org", identifier1 = "775671356")
        )

        val result = payeeService.addIban(
            userId,
            payee.id,
            org.commonlink.dto.AddIbanRequest(iban = validIban)
        )

        assertEquals(1, result.ibans.size)
        assertEquals(validIban, result.ibans[0].iban)
        assertEquals(IbanVerificationStatus.FORMAT_VALID, result.ibans[0].status)
    }

    @Test
    fun `addIban - invalid IBAN gets INVALID status`() {
        val payee = payeeService.createPayee(
            userId,
            org.commonlink.dto.CreatePayeeRequest(name = "Test Org", identifier1 = "775671356")
        )

        val result = payeeService.addIban(
            userId,
            payee.id,
            org.commonlink.dto.AddIbanRequest(iban = invalidIban)
        )

        assertEquals(1, result.ibans.size)
        assertEquals(IbanVerificationStatus.INVALID, result.ibans[0].status)
    }

    // ── deletePayee ────────────────────────────────────────────────────────────

    @Test
    fun `deletePayee - removes the payee`() {
        val payee = payeeService.createPayee(
            userId,
            org.commonlink.dto.CreatePayeeRequest(name = "Test Org", identifier1 = "775671356")
        )

        payeeService.deletePayee(userId, payee.id)

        val remaining = payeeService.listPayees(userId)
        assertTrue(remaining.isEmpty())
    }

    // ── deleteIban ────────────────────────────────────────────────────────────

    @Test
    fun `deleteIban - removes the IBAN`() {
        val payee = payeeService.createPayee(
            userId,
            org.commonlink.dto.CreatePayeeRequest(name = "Test Org", identifier1 = "775671356")
        )
        val withIban = payeeService.addIban(
            userId,
            payee.id,
            org.commonlink.dto.AddIbanRequest(iban = validIban)
        )
        val ibanId = withIban.ibans[0].id

        payeeService.deleteIban(userId, payee.id, ibanId)

        val updated = payeeService.listPayees(userId)
        assertTrue(updated[0].ibans.isEmpty())
    }

    // ── verifyIbanVop ─────────────────────────────────────────────────────────

    @Test
    fun `verifyIbanVop - returns VOP result for FORMAT_VALID IBAN`() {
        val payee = payeeService.createPayee(
            userId,
            org.commonlink.dto.CreatePayeeRequest(name = "Test Org", identifier1 = "775671356")
        )
        val withIban = payeeService.addIban(
            userId,
            payee.id,
            org.commonlink.dto.AddIbanRequest(iban = validIban)
        )
        val ibanId = withIban.ibans[0].id

        val result = payeeService.verifyIbanVop(userId, payee.id, ibanId)

        assertNotNull(result.vopResult)
        assertNotNull(result.status)
        assertNotEquals(IbanVerificationStatus.FORMAT_VALID, result.status)
    }

    @Test
    fun `verifyIbanVop - throws UnprocessableEntityException for non FORMAT_VALID IBAN`() {
        val payee = payeeService.createPayee(
            userId,
            org.commonlink.dto.CreatePayeeRequest(name = "Test Org", identifier1 = "775671356")
        )
        val withIban = payeeService.addIban(
            userId,
            payee.id,
            org.commonlink.dto.AddIbanRequest(iban = invalidIban)
        )
        val ibanId = withIban.ibans[0].id

        assertThrows<UnprocessableEntityException> {
            payeeService.verifyIbanVop(userId, payee.id, ibanId)
        }
    }
}

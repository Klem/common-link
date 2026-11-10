package org.commonlink.service

import org.commonlink.dto.CreatePayoutRequest
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.PayoutErrorCode
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.exception.FundsNotAvailableException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.PayeeIbanRepository
import org.commonlink.repository.PayeeRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves the guarantees of the disbursement check on a real PostgreSQL (the campaign row lock does not exist on H2):
 * - **concurrency**: two simultaneous payout creations whose sum exceeds the available donations — exactly one passes,
 *   the other gets the coded `FUNDS_NOT_AVAILABLE` refusal;
 * - **invariant**: after a sequence of creations, confirmations, cancellations and failures, Σ CONFIRMED + PENDING
 *   payouts never exceeds Σ available donations of the campaign.
 *
 * Not transactional: each service call commits on its own, as in production, so the two threads really contend on
 * the lock. Each test works on its own campaign. Every campaign also holds a confirmed HelloAsso donation not yet
 * available, which must never be spendable.
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayoutFundsAvailabilityConcurrencyTest {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        /** Same explicit override as [FundsAvailabilityGoldenTest]: the shared `@ServiceConnection` leaves H2 in place. */
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            postgres.start()
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }

    @Autowired private lateinit var payoutService: PayoutService
    @Autowired private lateinit var payoutConfirmer: PayoutConfirmer
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var payeeRepository: PayeeRepository
    @Autowired private lateinit var payeeIbanRepository: PayeeIbanRepository
    @Autowired private lateinit var payoutRepository: PayoutRepository

    private lateinit var associationUserId: UUID
    private lateinit var associationId: UUID
    private lateinit var donorId: UUID
    private lateinit var payeeId: UUID
    private lateinit var ibanId: UUID

    @BeforeAll
    fun seedAssociation() {
        donorId = donorProfileRepository.save(
            TestFixtures.donorProfile(userRepository.save(TestFixtures.donorUser(email = "donor-concurrency@example.com"))),
        ).id!!
        val assocUser = userRepository.save(TestFixtures.associationUser(email = "assoc-concurrency@example.com"))
        associationUserId = assocUser.id!!
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        associationId = assoc.id!!
        val payee = payeeRepository.save(TestFixtures.payee(assoc))
        payeeId = payee.id!!
        ibanId = payeeIbanRepository.save(TestFixtures.payeeIban(payee)).id!!
    }

    /** A campaign with [available] of available Mollie donations, plus 500.00 confirmed at HelloAsso, not available. */
    private fun campaignWith(vararg available: String): UUID {
        val campaign = campaignRepository.save(
            TestFixtures.campaign(associationProfileRepository.findById(associationId).get(), status = CampaignStatus.LIVE),
        )
        val donor = donorProfileRepository.findById(donorId).get()
        available.forEach { amount ->
            donationRepository.save(
                TestFixtures.donation(donor, campaign, amount = BigDecimal(amount), providerRef = "mollie:tr_${UUID.randomUUID()}")
                    .also { it.fundsAvailableAt = it.confirmedAt },
            )
        }
        donationRepository.save(
            TestFixtures.donation(donor, campaign, amount = BigDecimal("500.00"), providerRef = "helloasso:${UUID.randomUUID()}"),
        )
        return campaign.id!!
    }

    private fun create(campaignId: UUID, amount: String): UUID =
        payoutService.create(
            campaignId,
            CreatePayoutRequest(payeeId, ibanId, BigDecimal(amount), PayoutKind.EXPENSE, "60-mat",
                "Achat matériel pédagogique — facture FAC-001"),
            associationUserId,
        ).id

    /** Σ CONFIRMED + PENDING payouts ≤ Σ available donations. */
    private fun assertInvariant(campaignId: UUID, step: String) {
        val committed = payoutRepository.findAll()
            .filter { it.campaign.id == campaignId && it.status in setOf(PayoutStatus.CONFIRMED, PayoutStatus.PENDING) }
            .sumOf { it.amount }
        val available = donationRepository.sumAvailableAmountByCampaignId(campaignId) ?: BigDecimal.ZERO
        assertTrue(committed <= available, "$step: committed $committed > available $available")
    }

    @Test
    fun `two simultaneous creations exceeding the available balance — exactly one passes`() {
        val campaignId = campaignWith("60.00", "40.00")
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = List(2) {
                executor.submit<Result<UUID>> {
                    barrier.await(10, TimeUnit.SECONDS)
                    runCatching { create(campaignId, "60.00") }
                }
            }.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it.isSuccess }, "results: $results")
            val refusal = results.single { it.isFailure }.exceptionOrNull()
            assertTrue(refusal is FundsNotAvailableException, "expected FUNDS_NOT_AVAILABLE, got $refusal")
            assertEquals(0, BigDecimal("40.00").compareTo((refusal as FundsNotAvailableException).disbursable))
            assertInvariant(campaignId, "after the race")
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `committed payouts never exceed the available donations across a lifecycle`() {
        val campaignId = campaignWith("200.00", "100.00")
        assertInvariant(campaignId, "start")

        val p1 = create(campaignId, "100.00")
        val p2 = create(campaignId, "150.00")
        assertThrows<FundsNotAvailableException> { create(campaignId, "50.01") }
        assertInvariant(campaignId, "two pending")

        payoutConfirmer.reserve(campaignId, p1)
        payoutConfirmer.finaliseSettled(p1, "tx-1")
        assertInvariant(campaignId, "p1 confirmed")

        payoutService.cancel(campaignId, p2, associationUserId)
        assertInvariant(campaignId, "p2 cancelled")

        val p3 = create(campaignId, "200.00")
        payoutConfirmer.reserve(campaignId, p3)
        payoutConfirmer.finaliseFailed(p3, "Rejected by the bank", BridgePaymentStatus.RJCT, PayoutErrorCode.AC04)
        assertInvariant(campaignId, "p3 failed")

        val p4 = create(campaignId, "200.00")
        assertThrows<FundsNotAvailableException> { create(campaignId, "0.01") }
        payoutConfirmer.reserve(campaignId, p4)
        payoutConfirmer.releaseReservation(p4, "Link expired", PayoutErrorCode.LINK_EXPIRED)
        assertInvariant(campaignId, "p4 released")

        // The 500.00 confirmed at HelloAsso never became spendable.
        val refusal = assertThrows<FundsNotAvailableException> { create(campaignId, "0.01") }
        assertEquals(0, BigDecimal.ZERO.compareTo(refusal.disbursable))
        assertInvariant(campaignId, "end")
    }
}

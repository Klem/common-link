package org.commonlink.service

import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Payee
import org.commonlink.entity.PayeeIban
import org.commonlink.entity.PayoutBlockingReason
import org.commonlink.entity.PayoutErrorCode
import org.commonlink.entity.PayoutStatus
import org.commonlink.exception.ConflictException
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Golden master of the FIFO allocation and of the three payout balances on a representative Mollie data set, frozen
 * on the code that preceded the switch to "available" donations (`funds_available_at`). That switch must leave every
 * value here unchanged for Mollie: if one moves, the change altered Mollie behaviour. Expected values are written by
 * hand, never recomputed.
 *
 * Data set — campaign A (230.00 confirmed, one unconfirmed donation):
 * - donations, in payment order: 100.00, 50.00, two of 30.00 paid at the same instant (tie broken by id), 20.00;
 * - payouts: CONFIRMED 120.00 then 45.00 (the second partly funds the first tied donation); PENDING 10.00 (not in
 *   flight), 15.00 (in flight at Bridge) and 5.00 (released back to not in flight); FAILED 7.00; CANCELLED 9.00.
 *
 * Campaign B: donations 80.00 then 40.00, one CONFIRMED payout of 100.00.
 *
 * Runs on a real PostgreSQL started by this class, not on the suite's H2: the confirm-time check takes row locks
 * (`PESSIMISTIC_WRITE`) that H2 rejects. The datasource is overridden explicitly because the shared
 * `@ServiceConnection` container does not replace the test profile's H2 URL.
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@Transactional
class FundsAvailabilityGoldenTest {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

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

    @Autowired private lateinit var donationService: DonationService
    @Autowired private lateinit var allocationService: DonationAllocationService
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
    @Autowired private lateinit var entityManager: jakarta.persistence.EntityManager

    private lateinit var associationUserId: UUID
    private lateinit var campaignA: UUID
    private lateinit var campaignB: UUID
    private lateinit var ibanId: UUID
    private lateinit var payee: Payee
    private lateinit var iban: PayeeIban

    private val label = "Achat matériel pédagogique — facture FAC-001"
    private val t0: Instant = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)

    @BeforeEach
    fun setup() {
        val donor = donorProfileRepository.save(
            TestFixtures.donorProfile(userRepository.save(TestFixtures.donorUser(email = "donor-golden@example.com"))),
        )
        val assocUser = userRepository.save(TestFixtures.associationUser(email = "assoc-golden@example.com"))
        associationUserId = assocUser.id!!
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val a = campaignRepository.save(TestFixtures.campaign(assoc, name = "Golden A", status = CampaignStatus.LIVE))
        val b = campaignRepository.save(TestFixtures.campaign(assoc, name = "Golden B", status = CampaignStatus.LIVE))
        campaignA = a.id!!
        campaignB = b.id!!
        payee = payeeRepository.save(TestFixtures.payee(assoc))
        iban = payeeIbanRepository.save(TestFixtures.payeeIban(payee))
        ibanId = iban.id!!

        // Every confirmed donation goes through the real confirmation path, dated at its payment time.
        // Each confirmation clears the persistence context (campaign raised bulk update): ids only from here.
        pay(donor.id!!, campaignA, "100.00", 0)
        pay(donor.id!!, campaignA, "50.00", 10)
        pay(donor.id!!, campaignA, "30.00", 20)
        pay(donor.id!!, campaignA, "30.00", 20)
        pay(donor.id!!, campaignA, "20.00", 30)
        pay(donor.id!!, campaignB, "80.00", 0)
        pay(donor.id!!, campaignB, "40.00", 10)
        donationRepository.save(
            TestFixtures.donation(
                donorProfileRepository.findById(donor.id!!).get(), campaignRepository.findById(campaignA).get(),
                amount = BigDecimal("999.00"), providerRef = "mollie:tr_unconfirmed", confirmedAt = null,
            ),
        )

        payout(campaignA, "120.00", PayoutStatus.CONFIRMED, confirmedMinute = 40)
        payout(campaignA, "45.00", PayoutStatus.CONFIRMED, confirmedMinute = 50)
        payout(campaignA, "10.00", PayoutStatus.PENDING)
        payout(campaignA, "15.00", PayoutStatus.PENDING, bridgeStatus = BridgePaymentStatus.PDNG)
        val released = payout(campaignA, "5.00", PayoutStatus.PENDING, bridgeStatus = BridgePaymentStatus.CREA)
        payoutConfirmer.releaseReservation(released, "Link expired", PayoutErrorCode.LINK_EXPIRED)
        payout(campaignA, "7.00", PayoutStatus.FAILED)
        payout(campaignA, "9.00", PayoutStatus.CANCELLED)
        payout(campaignB, "100.00", PayoutStatus.CONFIRMED, confirmedMinute = 40)
        entityManager.flush()
        entityManager.clear()
    }

    private fun pay(donorId: UUID, campaignId: UUID, amount: String, minute: Long) {
        donationService.recordPayment(
            "mollie:tr_${UUID.randomUUID()}", donorId, campaignId, BigDecimal(amount), t0.plus(minute, ChronoUnit.MINUTES),
        )
    }

    private fun payout(
        campaignId: UUID,
        amount: String,
        status: PayoutStatus,
        confirmedMinute: Long? = null,
        bridgeStatus: BridgePaymentStatus? = null,
    ): UUID {
        val campaign: Campaign = campaignRepository.findById(campaignId).get()
        val payout = TestFixtures.payout(
            campaign, payeeRepository.findById(payee.id!!).get(), payeeIbanRepository.findById(ibanId).get(),
            amount = BigDecimal(amount), status = status,
        )
        payout.confirmedAt = confirmedMinute?.let { t0.plus(it, ChronoUnit.MINUTES) }
        payout.bridgeStatus = bridgeStatus
        return payoutRepository.saveAndFlush(payout).id
    }

    /** (amount, used, remaining) of each allocation line, in allocation order. */
    private fun allocation(campaignId: UUID): List<Triple<String, String, String>> =
        allocationService.allocateCampaign(campaignId).map { line ->
            val amount = line.usedAmount + line.remainingAmount
            Triple(amount.setScale(2).toPlainString(), line.usedAmount.setScale(2).toPlainString(), line.remainingAmount.setScale(2).toPlainString())
        }

    private fun assertAmount(expected: String, actual: BigDecimal, what: String) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "$what: expected $expected, was $actual")

    @Test
    fun `every confirmed donation of the data set is available`() {
        val confirmed = donationRepository.findAll().filter { it.confirmedAt != null && it.campaign.id in setOf(campaignA, campaignB) }
        assertEquals(7, confirmed.size)
        confirmed.forEach { assertNotNull(it.fundsAvailableAt, "donation ${it.id} has no fundsAvailableAt") }
    }

    @Test
    fun `campaign raised equals the sum of its confirmed donations`() {
        assertAmount("230.00", campaignRepository.findById(campaignA).get().raised, "raised A")
        assertAmount("120.00", campaignRepository.findById(campaignB).get().raised, "raised B")
    }

    @Test
    fun `FIFO allocation is frozen`() {
        assertEquals(
            listOf(
                Triple("100.00", "100.00", "0.00"),
                Triple("50.00", "50.00", "0.00"),
                Triple("30.00", "15.00", "15.00"),
                Triple("30.00", "0.00", "30.00"),
                Triple("20.00", "0.00", "20.00"),
            ),
            allocation(campaignA),
        )
        assertEquals(
            listOf(Triple("80.00", "80.00", "0.00"), Triple("40.00", "20.00", "20.00")),
            allocation(campaignB),
        )
    }

    @Test
    fun `payments summary is frozen`() {
        val a = payoutService.getSummary(campaignA, associationUserId)
        assertAmount("35.00", a.availableBalance, "available A")
        assertAmount("165.00", a.confirmedAmount, "confirmed A")
        assertAmount("30.00", a.pendingAmount, "pending A")
        val b = payoutService.getSummary(campaignB, associationUserId)
        assertAmount("20.00", b.availableBalance, "available B")
        assertAmount("100.00", b.confirmedAmount, "confirmed B")
        assertAmount("0.00", b.pendingAmount, "pending B")
    }

    @Test
    fun `blocking reasons at the available balance are frozen`() {
        assertEquals(emptyList<PayoutBlockingReason>(), payoutService.computeBlockingReasons(campaignA, ibanId, BigDecimal("35.00"), label, associationUserId))
        assertEquals(
            listOf(PayoutBlockingReason.INSUFFICIENT_BALANCE),
            payoutService.computeBlockingReasons(campaignA, ibanId, BigDecimal("35.01"), label, associationUserId),
        )
        assertEquals(emptyList<PayoutBlockingReason>(), payoutService.computeBlockingReasons(campaignB, ibanId, BigDecimal("20.00"), label, associationUserId))
        assertEquals(
            listOf(PayoutBlockingReason.INSUFFICIENT_BALANCE),
            payoutService.computeBlockingReasons(campaignB, ibanId, BigDecimal("20.01"), label, associationUserId),
        )
    }

    /** Confirmable at confirmation time = 230.00 − 165.00 CONFIRMED − 15.00 in flight = 50.00 on campaign A. */
    @Test
    fun `reserve accepts exactly the confirmable balance`() {
        val probeA = payout(campaignA, "50.00", PayoutStatus.PENDING)
        assertDoesNotThrow { payoutConfirmer.reserve(campaignA, probeA) }
        val probeB = payout(campaignB, "20.00", PayoutStatus.PENDING)
        assertDoesNotThrow { payoutConfirmer.reserve(campaignB, probeB) }
    }

    @Test
    fun `reserve refuses one cent above the confirmable balance`() {
        val probeA = payout(campaignA, "50.01", PayoutStatus.PENDING)
        assertThrows<ConflictException> { payoutConfirmer.reserve(campaignA, probeA) }
        val probeB = payout(campaignB, "20.01", PayoutStatus.PENDING)
        assertThrows<ConflictException> { payoutConfirmer.reserve(campaignB, probeB) }
    }
}

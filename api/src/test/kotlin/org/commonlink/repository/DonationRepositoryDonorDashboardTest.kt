package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.DonationReceipt
import org.commonlink.entity.MandateEligibility
import org.commonlink.entity.PayoutStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Integration coverage for the donor-dashboard queries added in Sprint 1.
 *
 * These run against a real PostgreSQL: the service-layer tests are mockk-based and would let a
 * malformed HQL, a broken nullable-parameter comparison or a fetch-join/count mismatch ship green.
 */
@ImportTestcontainers(TestcontainersConfig::class)
class DonationRepositoryDonorDashboardTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val donorProfileRepository: DonorProfileRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val donationRepository: DonationRepository,
    @Autowired private val donationReceiptRepository: DonationReceiptRepository,
    @Autowired private val fiscalMandateRepository: FiscalMandateRepository,
    @Autowired private val payeeRepository: PayeeRepository,
    @Autowired private val payeeIbanRepository: PayeeIbanRepository,
    @Autowired private val payoutRepository: PayoutRepository,
    @Autowired private val entityManager: jakarta.persistence.EntityManager,
) : AbstractRepositoryTest() {

    private lateinit var donorId: UUID
    private lateinit var otherDonorId: UUID
    private lateinit var assocAId: UUID
    private lateinit var assocBId: UUID
    private lateinit var campaignA1Id: UUID
    private lateinit var campaignA2Id: UUID
    private lateinit var campaignB1Id: UUID

    private val in2024 = Instant.parse("2024-06-15T10:00:00Z")
    private val in2025 = Instant.parse("2025-03-01T10:00:00Z")
    private val in2026 = Instant.parse("2026-02-01T10:00:00Z")

    /**
     * Instants as they come **back** from the database, captured after a flush/clear.
     *
     * A `TIMESTAMPTZ` round trip is shifted by the JVM's own offset on a developer machine running
     * a non-UTC zone (Europe/Paris here): 10:00Z goes in, 08:00Z comes out in June. Entity reads and
     * scalar projections agree with each other, so the queries under test are unaffected — but an
     * assertion against the literal would fail for a reason that has nothing to do with the query.
     * Asserting against the stored value keeps these tests about `MAX()`, filtering and grouping.
     */
    private lateinit var stored2024: Instant
    private lateinit var stored2025: Instant

    @BeforeEach
    fun setup() {
        val assocUserA = userRepository.save(TestFixtures.associationUser(email = "a@test.com"))
        val assocA = associationProfileRepository.save(
            TestFixtures.associationProfile(assocUserA, name = "Alpha Asso", identifier = "111111111")
        )
        assocAId = assocA.id!!

        val assocUserB = userRepository.save(TestFixtures.associationUser(email = "b@test.com"))
        val assocB = associationProfileRepository.save(
            TestFixtures.associationProfile(assocUserB, name = "Beta Asso", identifier = "222222222")
        )
        assocBId = assocB.id!!

        val campaignA1 = campaignRepository.save(
            TestFixtures.campaign(assocA, name = "A1", status = CampaignStatus.LIVE).apply { category = "Education" }
        )
        campaignA1Id = campaignA1.id!!
        val campaignA2 = campaignRepository.save(
            TestFixtures.campaign(assocA, name = "A2", status = CampaignStatus.LIVE).apply { category = "Sante" }
        )
        campaignA2Id = campaignA2.id!!
        val campaignB1 = campaignRepository.save(
            TestFixtures.campaign(assocB, name = "B1", status = CampaignStatus.LIVE).apply { category = "Environnement" }
        )
        campaignB1Id = campaignB1.id!!

        val donorUser = userRepository.save(TestFixtures.donorUser(email = "donor@test.com"))
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        donorId = donor.id!!

        val otherUser = userRepository.save(TestFixtures.donorUser(email = "other@test.com"))
        val otherDonor = donorProfileRepository.save(TestFixtures.donorProfile(otherUser, displayName = "Other"))
        otherDonorId = otherDonor.id!!

        // Alpha: 100 € in 2024 (campaign A1, with receipt), 50 € in 2025 (campaign A2)
        val donationWithReceipt = donationRepository.save(
            TestFixtures.donation(donor, campaignA1, amount = BigDecimal("100.00"), confirmedAt = in2024)
        )
        donationReceiptRepository.save(
            DonationReceipt(
                donation = donationWithReceipt,
                receiptNumber = "2024-0001",
                pdfBytes = byteArrayOf(1, 2, 3),
                generatedAt = in2024,
            )
        )
        donationRepository.save(
            TestFixtures.donation(donor, campaignA2, amount = BigDecimal("50.00"), confirmedAt = in2025)
        )
        // Beta: 200 € in 2026 (campaign B1) — most recent donation overall
        donationRepository.save(
            TestFixtures.donation(donor, campaignB1, amount = BigDecimal("200.00"), confirmedAt = in2026)
        )
        // Not confirmed — must be invisible everywhere
        donationRepository.save(
            TestFixtures.donation(donor, campaignA1, amount = BigDecimal("999.00"), confirmedAt = null)
        )
        // Another donor's donation — must never leak into the donor's figures
        donationRepository.save(
            TestFixtures.donation(otherDonor, campaignA1, amount = BigDecimal("777.00"), confirmedAt = in2025)
        )

        entityManager.flush()
        entityManager.clear()
        val roundTripped = donationRepository.findByDonorIdFiltered(donorId, null, null, PageRequest.of(0, 20))
            .content.associateBy { it.amount.stripTrailingZeros() }
        stored2024 = roundTripped[BigDecimal("100.00").stripTrailingZeros()]!!.confirmedAt!!
        stored2025 = roundTripped[BigDecimal("50.00").stripTrailingZeros()]!!.confirmedAt!!
    }

    // ── History ───────────────────────────────────────────────────────────

    @Test
    fun `findByDonorIdFiltered returns confirmed donations newest first, both filters disabled`() {
        val page = donationRepository.findByDonorIdFiltered(donorId, null, null, PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content.map { it.amount })
            .containsExactly(BigDecimal("200.00"), BigDecimal("50.00"), BigDecimal("100.00"))
        // JOIN FETCH: campaign and association are already loaded
        assertThat(page.content[0].campaign.association.name).isEqualTo("Beta Asso")
    }

    @Test
    fun `findByDonorIdFiltered filters by association`() {
        val page = donationRepository.findByDonorIdFiltered(donorId, assocAId, null, PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content.map { it.campaign.id }).containsExactly(campaignA2Id, campaignA1Id)
    }

    @Test
    fun `findByDonorIdFiltered filters by year`() {
        val page = donationRepository.findByDonorIdFiltered(donorId, null, 2025, PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content[0].amount).isEqualByComparingTo("50.00")
    }

    @Test
    fun `findByDonorIdFiltered combines association and year filters`() {
        val match = donationRepository.findByDonorIdFiltered(donorId, assocAId, 2024, PageRequest.of(0, 20))
        assertThat(match.totalElements).isEqualTo(1)
        assertThat(match.content[0].amount).isEqualByComparingTo("100.00")

        // Same year, but the association has no donation that year
        val noMatch = donationRepository.findByDonorIdFiltered(donorId, assocBId, 2024, PageRequest.of(0, 20))
        assertThat(noMatch.totalElements).isZero()
    }

    @Test
    fun `findByDonorIdFiltered paginates with a correct total`() {
        val page0 = donationRepository.findByDonorIdFiltered(donorId, null, null, PageRequest.of(0, 2))

        assertThat(page0.content).hasSize(2)
        assertThat(page0.totalElements).isEqualTo(3)
        assertThat(page0.totalPages).isEqualTo(2)
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    @Test
    fun `stats queries exclude unconfirmed donations and other donors`() {
        assertThat(donationRepository.sumConfirmedAmountByDonorId(donorId)).isEqualByComparingTo("350.00")
        assertThat(donationRepository.countConfirmedByDonorId(donorId)).isEqualTo(3)
        assertThat(donationRepository.countDistinctAssociationsByDonorId(donorId)).isEqualTo(2)
    }

    @Test
    fun `sumConfirmedAmountByDonorId returns zero for a donor with no donation`() {
        assertThat(donationRepository.sumConfirmedAmountByDonorId(UUID.randomUUID())).isEqualByComparingTo("0")
    }

    // ── Filter options ────────────────────────────────────────────────────

    @Test
    fun `findDistinctYearsByDonorId returns the donation years, most recent first`() {
        assertThat(donationRepository.findDistinctYearsByDonorId(donorId)).containsExactly(2026, 2025, 2024)
    }

    @Test
    fun `findDistinctAssociationsByDonorId returns funded associations alphabetically`() {
        val rows = donationRepository.findDistinctAssociationsByDonorId(donorId)

        assertThat(rows.map { it.getName() }).containsExactly("Alpha Asso", "Beta Asso")
        assertThat(rows.map { it.getId() }).containsExactly(assocAId, assocBId)
    }

    // ── Association aggregates ────────────────────────────────────────────

    @Test
    fun `findAssociationAggregatesByDonorId sums per association and counts distinct campaigns`() {
        val rows = donationRepository.findAssociationAggregatesByDonorId(donorId).associateBy { it.getAssociationId() }

        val alpha = rows[assocAId]!!
        assertThat(alpha.getName()).isEqualTo("Alpha Asso")
        assertThat(alpha.getTotalAmount()).isEqualByComparingTo("150.00")
        assertThat(alpha.getCampaignCount()).isEqualTo(2)
        assertThat(alpha.getLastDonationAt()).isEqualTo(stored2025)

        val beta = rows[assocBId]!!
        assertThat(beta.getTotalAmount()).isEqualByComparingTo("200.00")
        assertThat(beta.getCampaignCount()).isEqualTo(1)
    }

    @Test
    fun `findAssociationCategoriesByDonorId is ordered oldest first so the last row wins`() {
        val byAssociation = donationRepository.findAssociationCategoriesByDonorId(donorId)
            .associateBy({ it.getAssociationId() }, { it.getCategory() })

        // Alpha: A1 (Education, 2024) then A2 (Sante, 2025) → the most recent one wins
        assertThat(byAssociation[assocAId]).isEqualTo("Sante")
        assertThat(byAssociation[assocBId]).isEqualTo("Environnement")
    }

    // ── Tax reduction input ───────────────────────────────────────────────

    @Test
    fun `findReceiptedRowsByDonorId returns only donations carrying a receipt`() {
        val rows = donationRepository.findReceiptedRowsByDonorId(donorId)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].getAssociationId()).isEqualTo(assocAId)
        assertThat(rows[0].getAmount()).isEqualByComparingTo("100.00")
        assertThat(rows[0].getConfirmedAt()).isEqualTo(stored2024)
    }

    // ── Read scope ────────────────────────────────────────────────────────

    @Test
    fun `existsConfirmedByDonorIdAndCampaignId answers on confirmed donations only`() {
        assertThat(donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignA1Id)).isTrue()
        assertThat(donationRepository.existsConfirmedByDonorIdAndCampaignId(otherDonorId, campaignB1Id)).isFalse()
    }

    // ── Published payouts ─────────────────────────────────────────────────

    @Test
    fun `countConfirmedByAssociationId counts confirmed payouts across campaigns`() {
        val assocA = associationProfileRepository.findById(assocAId).orElseThrow()
        val payee = payeeRepository.save(TestFixtures.payee(assocA))
        val iban = payeeIbanRepository.save(TestFixtures.payeeIban(payee))
        val campaignA1 = campaignRepository.findById(campaignA1Id).orElseThrow()
        val campaignA2 = campaignRepository.findById(campaignA2Id).orElseThrow()

        payoutRepository.save(TestFixtures.payout(campaignA1, payee, iban, status = PayoutStatus.CONFIRMED))
        payoutRepository.save(TestFixtures.payout(campaignA2, payee, iban, status = PayoutStatus.CONFIRMED))
        payoutRepository.save(TestFixtures.payout(campaignA1, payee, iban, status = PayoutStatus.PENDING))

        assertThat(payoutRepository.countConfirmedByAssociationId(assocAId)).isEqualTo(2)
        assertThat(payoutRepository.countConfirmedByAssociationId(assocBId)).isZero()
    }

    // ── Historical mandate ────────────────────────────────────────────────

    @Test
    fun `findActiveAt resolves the mandate in force at the donation date, not today`() {
        val assocA = associationProfileRepository.findById(assocAId).orElseThrow()
        val revoked = TestFixtures.fiscalMandate(
            assocA,
            eligibility = MandateEligibility.OIG_75_COLUCHE,
            reference = "MND-2024-0001",
            signedAt = in2024.minus(30, ChronoUnit.DAYS),
        )
        revoked.revokedAt = in2025
        fiscalMandateRepository.save(revoked)

        // At the 2024 donation date the Coluche mandate was in force…
        assertThat(fiscalMandateRepository.findActiveAt(assocAId, in2024)?.eligibility)
            .isEqualTo(MandateEligibility.OIG_75_COLUCHE)
        // …and nothing is in force after its revocation
        assertThat(fiscalMandateRepository.findActiveAt(assocAId, in2026)).isNull()
        // …nor before it was signed
        assertThat(fiscalMandateRepository.findActiveAt(assocAId, Instant.parse("2020-01-01T00:00:00Z"))).isNull()
    }
}

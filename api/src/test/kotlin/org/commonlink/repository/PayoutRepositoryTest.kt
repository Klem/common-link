package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@ImportTestcontainers(TestcontainersConfig::class)
class PayoutRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val payeeRepository: PayeeRepository,
    @Autowired private val payeeIbanRepository: PayeeIbanRepository,
    @Autowired private val payoutRepository: PayoutRepository,
) : AbstractRepositoryTest() {

    private lateinit var campaignId: java.util.UUID

    @BeforeEach
    fun setup() {
        val assocUser = userRepository.save(TestFixtures.associationUser())
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))
        campaignId = campaign.id!!

        val payee = payeeRepository.save(TestFixtures.payee(assoc))
        val iban  = payeeIbanRepository.save(TestFixtures.payeeIban(payee))

        payoutRepository.save(TestFixtures.payout(campaign, payee, iban,
            amount = BigDecimal("300"), status = PayoutStatus.CONFIRMED))
        payoutRepository.save(TestFixtures.payout(campaign, payee, iban,
            amount = BigDecimal("200"), status = PayoutStatus.CONFIRMED))
        payoutRepository.save(TestFixtures.payout(campaign, payee, iban,
            amount = BigDecimal("150"), status = PayoutStatus.PENDING))
        payoutRepository.save(TestFixtures.payout(campaign, payee, iban,
            amount = BigDecimal("100"), status = PayoutStatus.FAILED))
    }

    @Test
    fun `sumAmountByCampaignIdAndStatus returns correct confirmed sum`() {
        val sum = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED)
        assertThat(sum).isEqualByComparingTo("500")
    }

    @Test
    fun `sumAmountByCampaignIdAndStatus returns correct pending sum`() {
        val sum = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.PENDING)
        assertThat(sum).isEqualByComparingTo("150")
    }

    @Test
    fun `countByCampaignId counts all statuses`() {
        assertThat(payoutRepository.countByCampaignId(campaignId)).isEqualTo(4L)
    }

    @Test
    fun `countByCampaignIdAndStatus counts confirmed only`() {
        assertThat(payoutRepository.countByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED)).isEqualTo(2L)
    }

    @Test
    fun `findByCampaignIdOrderByCreatedAtDesc returns page ordered newest first`() {
        val page = payoutRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId, PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(4)
    }

    @Test
    fun `routing projections bind their aliases and never manage the payout`() {
        // Mocked repositories cannot catch an alias that does not match a getter, and the identity
        // map is exactly what these projections exist to avoid: returning the entity here loaded it
        // into the request-scoped persistence context, and the confirmer's later `SELECT … FOR
        // UPDATE` was then answered from that map with a state read before the concurrent thread
        // committed. Only a real database exercises the binding.
        val payout = payoutRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId, PageRequest.of(0, 1))
            .content.first()
        payout.bridgePaymentLinkId = "pl_routing_1"
        payoutRepository.saveAndFlush(payout)

        val byLink = payoutRepository.findRoutingByBridgePaymentLinkId("pl_routing_1")
        assertThat(byLink).isNotNull
        assertThat(byLink!!.id).isEqualTo(payout.id)
        assertThat(byLink.bridgePaymentLinkId).isEqualTo("pl_routing_1")

        val byId = payoutRepository.findRoutingById(payout.id)
        assertThat(byId).isNotNull
        assertThat(byId!!.bridgePaymentLinkId).isEqualTo("pl_routing_1")

        assertThat(payoutRepository.findRoutingByBridgePaymentLinkId("pl_unknown")).isNull()
    }

    @Test
    fun `findStaleInFlight returns only engaged payouts whose state has stopped moving`() {
        val all = payoutRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId, PageRequest.of(0, 10)).content
        val stale = all.first { it.status == PayoutStatus.PENDING }
        stale.bridgePaymentLinkId = "pl_stale"
        stale.bridgeStatus = BridgePaymentStatus.PDNG
        stale.bridgeSyncedAt = Instant.now().minus(Duration.ofDays(5))

        // Engaged but fresh: not stale yet.
        val fresh = all.first { it.status == PayoutStatus.CONFIRMED }
        fresh.status = PayoutStatus.PENDING
        fresh.bridgePaymentLinkId = "pl_fresh"
        fresh.bridgeStatus = BridgePaymentStatus.PDNG
        fresh.bridgeSyncedAt = Instant.now()
        payoutRepository.saveAllAndFlush(listOf(stale, fresh))

        val found = payoutRepository.findStaleInFlight(Instant.now().minus(Duration.ofDays(1)))

        assertThat(found.map { it.id }).containsExactly(stale.id)
        // The projection has to carry the age the sweep reasons on, and the state it escalates on.
        assertThat(found.first().bridgeStatus).isEqualTo(BridgePaymentStatus.PDNG)
        assertThat(found.first().bridgeSyncedAt).isNotNull
        assertThat(found.first().bridgePaymentLinkId).isEqualTo("pl_stale")
    }

    // No test covers findByIdForUpdate: this slice rejects the `for no key update` Hibernate emits
    // for a PESSIMISTIC_WRITE, with error `[42000-240]` — an H2 code, so it is not running on the
    // PostgreSQL container its own KDoc describes. CampaignRepository.findByIdForUpdate is
    // untested for the same reason. Worth sorting out, but as its own task.

    @Test
    fun `empty campaign - sums return zero`() {
        val assocUser2 = userRepository.save(TestFixtures.associationUser(email = "other@test.com"))
        val assoc2 = associationProfileRepository.save(TestFixtures.associationProfile(assocUser2))
        val emptyCampaign = campaignRepository.save(TestFixtures.campaign(assoc2))

        val sum = payoutRepository.sumAmountByCampaignIdAndStatus(emptyCampaign.id!!, PayoutStatus.CONFIRMED)
        assertThat(sum ?: BigDecimal.ZERO).isEqualByComparingTo("0")
        assertThat(payoutRepository.countByCampaignId(emptyCampaign.id!!)).isEqualTo(0L)
    }
}

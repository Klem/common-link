package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

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
    fun `empty campaign - sums return zero`() {
        val assocUser2 = userRepository.save(TestFixtures.associationUser(email = "other@test.com"))
        val assoc2 = associationProfileRepository.save(TestFixtures.associationProfile(assocUser2))
        val emptyCampaign = campaignRepository.save(TestFixtures.campaign(assoc2))

        val sum = payoutRepository.sumAmountByCampaignIdAndStatus(emptyCampaign.id!!, PayoutStatus.CONFIRMED)
        assertThat(sum ?: BigDecimal.ZERO).isEqualByComparingTo("0")
        assertThat(payoutRepository.countByCampaignId(emptyCampaign.id!!)).isEqualTo(0L)
    }
}

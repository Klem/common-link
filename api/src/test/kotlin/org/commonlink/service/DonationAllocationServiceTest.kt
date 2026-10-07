package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Donation
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.Payee
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonationAllocationServiceTest {

    private val donationRepository = mockk<DonationRepository>()
    private val payoutRepository = mockk<PayoutRepository>()
    private val service = DonationAllocationService(donationRepository, payoutRepository)

    private val campaignId = UUID.randomUUID()

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val association = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789")
        .setId(UUID.randomUUID())
    private val campaign = Campaign(association = association, name = "Camp", emoji = "🌍", goal = BigDecimal("10000"), status = CampaignStatus.LIVE)
        .setId(campaignId)
    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
    private val donor = DonorProfile(user = donorUser).setId(UUID.randomUUID())
    private val payee = Payee(association = association, name = "Fournisseur").setId(UUID.randomUUID())

    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    private fun donation(amount: String, confirmedAt: Instant): Donation =
        Donation(donor = donor, campaign = campaign, amount = BigDecimal(amount), providerRef = "mollie:tr_${UUID.randomUUID()}", confirmedAt = confirmedAt)
            .setId(UUID.randomUUID())

    private fun payout(amount: String, confirmedAt: Instant, label: String = "Achat matériel"): Payout =
        Payout(
            campaign = campaign, payee = payee, payeeIbanId = UUID.randomUUID(), payeeIbanValue = "FR7630006000011234567890189",
            amount = BigDecimal(amount), kind = PayoutKind.EXPENSE, typeCode = "60-mat", label = label,
            status = PayoutStatus.CONFIRMED, confirmedAt = confirmedAt,
        )

    @Test
    fun `one payout consumed by a single donation`() {
        val d1 = donation("100", t0)
        val p1 = payout("60", t0.plusSeconds(1))
        every { donationRepository.findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAsc(campaignId) } returns listOf(d1)
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns listOf(p1)

        val result = service.allocateCampaign(campaignId)

        assertThat(result).hasSize(1)
        with(result[0]) {
            assertThat(usedAmount).isEqualByComparingTo("60")
            assertThat(remainingAmount).isEqualByComparingTo("40")
            assertThat(fundedPayouts).hasSize(1)
            assertThat(fundedPayouts[0].amountImputed).isEqualByComparingTo("60")
        }
    }

    @Test
    fun `a payout spanning two donations is imputed oldest donation first`() {
        val older = donation("50", t0)
        val newer = donation("50", t0.plusSeconds(60))
        val p1 = payout("80", t0.plusSeconds(120))
        every { donationRepository.findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAsc(campaignId) } returns listOf(older, newer)
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns listOf(p1)

        val result = service.allocateCampaign(campaignId)

        val byId = result.associateBy { it.donationId }
        assertThat(byId.getValue(older.id!!).usedAmount).isEqualByComparingTo("50")
        assertThat(byId.getValue(older.id!!).remainingAmount).isEqualByComparingTo("0")
        assertThat(byId.getValue(newer.id!!).usedAmount).isEqualByComparingTo("30")
        assertThat(byId.getValue(newer.id!!).remainingAmount).isEqualByComparingTo("20")
    }

    @Test
    fun `one donation funding two payouts partially`() {
        val d1 = donation("100", t0)
        val p1 = payout("30", t0.plusSeconds(1), "Loyer")
        val p2 = payout("40", t0.plusSeconds(2), "Matériel")
        every { donationRepository.findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAsc(campaignId) } returns listOf(d1)
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns listOf(p1, p2)

        val result = service.allocateCampaign(campaignId)

        with(result[0]) {
            assertThat(usedAmount).isEqualByComparingTo("70")
            assertThat(remainingAmount).isEqualByComparingTo("30")
            assertThat(fundedPayouts).hasSize(2)
            assertThat(fundedPayouts.map { it.label }).containsExactly("Loyer", "Matériel")
        }
    }

    @Test
    fun `no confirmed payout leaves every donation fully unused`() {
        val d1 = donation("100", t0)
        val d2 = donation("50", t0.plusSeconds(1))
        every { donationRepository.findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAsc(campaignId) } returns listOf(d1, d2)
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns emptyList()

        val result = service.allocateCampaign(campaignId)

        assertThat(result).allSatisfy {
            assertThat(it.usedAmount).isEqualByComparingTo("0")
        }
        assertThat(result.first { it.donationId == d1.id }.remainingAmount).isEqualByComparingTo("100")
        assertThat(result.first { it.donationId == d2.id }.remainingAmount).isEqualByComparingTo("50")
    }

    @Test
    fun `allocation follows strict chronological order, not donation size`() {
        // Without the ASC-by-confirmedAt ordering, a naive implementation could pick the larger
        // donation first; here the small, older donation must be exhausted before the large one
        // is touched at all.
        val olderSmall = donation("10", t0)
        val newerLarge = donation("1000", t0.plusSeconds(1))
        val p1 = payout("15", t0.plusSeconds(2))
        every { donationRepository.findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAsc(campaignId) } returns listOf(olderSmall, newerLarge)
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns listOf(p1)

        val result = service.allocateCampaign(campaignId)

        val byId = result.associateBy { it.donationId }
        assertThat(byId.getValue(olderSmall.id!!).usedAmount).isEqualByComparingTo("10")
        assertThat(byId.getValue(olderSmall.id!!).remainingAmount).isEqualByComparingTo("0")
        assertThat(byId.getValue(newerLarge.id!!).usedAmount).isEqualByComparingTo("5")
        assertThat(byId.getValue(newerLarge.id!!).remainingAmount).isEqualByComparingTo("995")
    }

    @Test
    fun `only CONFIRMED payouts and non-null confirmedAt donations are ever requested from the repositories`() {
        every { donationRepository.findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAsc(campaignId) } returns emptyList()
        every { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) } returns emptyList()

        service.allocateCampaign(campaignId)

        verify(exactly = 1) { donationRepository.findByCampaignIdAndConfirmedAtIsNotNullOrderByConfirmedAtAsc(campaignId) }
        verify(exactly = 1) { payoutRepository.findByCampaignIdAndStatusOrderByConfirmedAtAsc(campaignId, PayoutStatus.CONFIRMED) }
    }
}

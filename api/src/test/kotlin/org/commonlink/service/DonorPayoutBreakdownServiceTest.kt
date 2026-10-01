package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.DonationAllocationDto
import org.commonlink.dto.FundedPayoutShareDto
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
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.access.AccessDeniedException
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorPayoutBreakdownServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository = mockk<DonationRepository>()
    private val payoutRepository = mockk<PayoutRepository>()
    private val donationAllocationService = mockk<DonationAllocationService>()
    private val donorReadScope = DonorReadScope(donorProfileRepository, donationRepository)

    private val service = DonorPayoutBreakdownService(
        donorReadScope, payoutRepository, donationRepository, donationAllocationService,
    )

    private val userId = UUID.randomUUID()
    private val donorId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val payoutId = UUID.randomUUID()

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val association = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789").setId(UUID.randomUUID())
    private val campaign = Campaign(
        association = association, name = "Camp", emoji = "🌍", goal = BigDecimal("10000"), status = CampaignStatus.LIVE,
    ).setId(campaignId)
    private val payee = Payee(association = association, name = "Fournisseur").setId(UUID.randomUUID())

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
    private val donor = DonorProfile(user = donorUser).setId(donorId)

    private fun otherDonor(email: String): DonorProfile {
        val user = User(email = email, role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
        return DonorProfile(user = user).setId(UUID.randomUUID())
    }

    private val payout = Payout(
        campaign = campaign, payee = payee, payeeIbanId = UUID.randomUUID(),
        payeeIbanValue = "FR7630006000011234567890189", amount = BigDecimal("100"), kind = PayoutKind.EXPENSE,
        typeCode = "60-mat", label = "Achat matériel", status = PayoutStatus.CONFIRMED,
        confirmedAt = Instant.parse("2026-01-10T00:00:00Z"),
    ).setId(payoutId)

    private fun donation(owner: DonorProfile, confirmedAt: Instant): Donation =
        Donation(
            donor = owner, campaign = campaign, amount = BigDecimal("10"),
            providerRef = "mollie:tr_${UUID.randomUUID()}", confirmedAt = confirmedAt,
        ).setId(UUID.randomUUID())

    private fun share(donationId: UUID, amountImputed: String) = DonationAllocationDto(
        donationId = donationId,
        usedAmount = BigDecimal(amountImputed),
        remainingAmount = BigDecimal.ZERO,
        fundedPayouts = listOf(
            FundedPayoutShareDto(
                payoutId = payoutId, label = payout.label,
                amountImputed = BigDecimal(amountImputed), confirmedAt = payout.confirmedAt!!,
            ),
        ),
    )

    private fun stubOwnership() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignId) } returns true
        every { payoutRepository.findById(payoutId) } returns Optional.of(payout)
    }

    @Test
    fun `only the viewing donor's own contribution is returned in full detail`() {
        stubOwnership()
        val mine = donation(donor, Instant.parse("2026-01-01T00:00:00Z"))
        every { donationAllocationService.allocateCampaign(campaignId) } returns listOf(share(mine.id!!, "100"))
        every { donationRepository.findAllById(listOf(mine.id!!)) } returns listOf(mine)

        val result = service.getBreakdown(userId, campaignId, payoutId)

        assertThat(result.myLines).hasSize(1)
        assertThat(result.myLines[0].donationId).isEqualTo(mine.id)
        assertThat(result.myTotal).isEqualByComparingTo("100")
        assertThat(result.othersTotal).isNull()
        assertThat(result.othersDonationCount).isEqualTo(0)
    }

    @Test
    fun `other donors' aggregate is withheld below the minimum of 3 contributing donations`() {
        stubOwnership()
        val mine = donation(donor, Instant.parse("2026-01-01T00:00:00Z"))
        val other1 = donation(otherDonor("o1@test.com"), Instant.parse("2026-01-02T00:00:00Z"))
        every { donationAllocationService.allocateCampaign(campaignId) } returns
            listOf(share(mine.id!!, "60"), share(other1.id!!, "40"))
        every { donationRepository.findAllById(listOf(mine.id!!, other1.id!!)) } returns listOf(mine, other1)

        val result = service.getBreakdown(userId, campaignId, payoutId)

        assertThat(result.myLines).hasSize(1)
        assertThat(result.myTotal).isEqualByComparingTo("60")
        // Only 1 external donation -- below the floor of 3, so the amount must never surface...
        assertThat(result.othersTotal).isNull()
        // ...but the count itself carries no monetary information, so it's never withheld -- this
        // is what lets the frontend distinguish "hidden" from "genuinely none" (see next test).
        assertThat(result.othersDonationCount).isEqualTo(1)
    }

    @Test
    fun `othersDonationCount is 0, not withheld, when no other donor contributed at all`() {
        stubOwnership()
        val mine = donation(donor, Instant.parse("2026-01-01T00:00:00Z"))
        every { donationAllocationService.allocateCampaign(campaignId) } returns listOf(share(mine.id!!, "100"))
        every { donationRepository.findAllById(listOf(mine.id!!)) } returns listOf(mine)

        val result = service.getBreakdown(userId, campaignId, payoutId)

        assertThat(result.othersTotal).isNull()
        assertThat(result.othersDonationCount).isEqualTo(0)
    }

    @Test
    fun `other donors' aggregate is exposed once at least 3 distinct donations contribute`() {
        stubOwnership()
        val mine = donation(donor, Instant.parse("2026-01-01T00:00:00Z"))
        val other1 = donation(otherDonor("o1@test.com"), Instant.parse("2026-01-02T00:00:00Z"))
        val other2 = donation(otherDonor("o2@test.com"), Instant.parse("2026-01-03T00:00:00Z"))
        val other3 = donation(otherDonor("o3@test.com"), Instant.parse("2026-01-04T00:00:00Z"))
        val ids = listOf(mine.id!!, other1.id!!, other2.id!!, other3.id!!)
        every { donationAllocationService.allocateCampaign(campaignId) } returns listOf(
            share(mine.id!!, "10"), share(other1.id!!, "30"), share(other2.id!!, "30"), share(other3.id!!, "30"),
        )
        every { donationRepository.findAllById(ids) } returns listOf(mine, other1, other2, other3)

        val result = service.getBreakdown(userId, campaignId, payoutId)

        assertThat(result.myLines).hasSize(1)
        assertThat(result.othersTotal).isEqualByComparingTo("90")
        assertThat(result.othersDonationCount).isEqualTo(3)
    }

    @Test
    fun `a donor with no contribution to this payout sees only the others aggregate`() {
        stubOwnership()
        val other1 = donation(otherDonor("o1@test.com"), Instant.parse("2026-01-02T00:00:00Z"))
        val other2 = donation(otherDonor("o2@test.com"), Instant.parse("2026-01-03T00:00:00Z"))
        val other3 = donation(otherDonor("o3@test.com"), Instant.parse("2026-01-04T00:00:00Z"))
        val ids = listOf(other1.id!!, other2.id!!, other3.id!!)
        every { donationAllocationService.allocateCampaign(campaignId) } returns
            listOf(share(other1.id!!, "30"), share(other2.id!!, "30"), share(other3.id!!, "40"))
        every { donationRepository.findAllById(ids) } returns listOf(other1, other2, other3)

        val result = service.getBreakdown(userId, campaignId, payoutId)

        assertThat(result.myLines).isEmpty()
        assertThat(result.myTotal).isEqualByComparingTo("0")
        assertThat(result.othersTotal).isEqualByComparingTo("100")
        assertThat(result.othersDonationCount).isEqualTo(3)
    }

    @Test
    fun `throws when the payout does not belong to the given campaign`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignId) } returns true
        val otherCampaign = Campaign(
            association = association, name = "Autre", emoji = "🌊", goal = BigDecimal("500"), status = CampaignStatus.LIVE,
        ).setId(UUID.randomUUID())
        val foreignPayout = Payout(
            campaign = otherCampaign, payee = payee, payeeIbanId = UUID.randomUUID(),
            payeeIbanValue = "FR7630006000011234567890189", amount = BigDecimal("50"), kind = PayoutKind.EXPENSE,
            typeCode = "60-mat", label = "Autre dépense", status = PayoutStatus.CONFIRMED,
            confirmedAt = Instant.parse("2026-01-10T00:00:00Z"),
        ).setId(payoutId)
        every { payoutRepository.findById(payoutId) } returns Optional.of(foreignPayout)

        assertThrows<AccessDeniedException> { service.getBreakdown(userId, campaignId, payoutId) }
    }

    @Test
    fun `throws NotFound when the payout does not exist`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignId) } returns true
        every { payoutRepository.findById(payoutId) } returns Optional.empty()

        assertThrows<NotFoundException> { service.getBreakdown(userId, campaignId, payoutId) }
    }

    @Test
    fun `throws AccessDenied when the donor has no confirmed donation on the campaign`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignId) } returns false

        assertThrows<AccessDeniedException> { service.getBreakdown(userId, campaignId, payoutId) }
    }
}

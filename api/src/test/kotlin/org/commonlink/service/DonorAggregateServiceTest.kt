package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Donation
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonationRepository.DonorAggregateRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorAggregateServiceTest {

    private val donationRepository            = mockk<DonationRepository>()
    private val campaignRepository            = mockk<CampaignRepository>()
    private val associationProfileRepository  = mockk<AssociationProfileRepository>()
    private val service = DonorAggregateService(donationRepository, campaignRepository, associationProfileRepository)

    private val userId       = UUID.fromString("00000000-0000-0000-0000-000000000000")  // JWT subject
    private val assocId      = UUID.fromString("00000000-0000-0000-0000-000000000001")  // AssociationProfile.id
    private val campaignId   = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val donorId      = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val donationId   = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val otherAssocId = UUID.fromString("00000000-0000-0000-0000-000000000099")

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL, emailVerified = true)
    private val assoc     = AssociationProfile(user = assocUser, name = "Asso Test", identifier = "123456789").setId(assocId)
    private val campaign  = Campaign(association = assoc, name = "Camp", emoji = "🌍", description = "d", goal = BigDecimal("1000"), status = CampaignStatus.LIVE).setId(campaignId)

    private val donorUser    = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, emailVerified = true)
    private val donorProfile = DonorProfile(user = donorUser, displayName = "Marie D.", anonymous = false).setId(donorId)

    private fun makeDonation(id: UUID = donationId, targetCampaign: Campaign = campaign) =
        Donation(donor = donorProfile, campaign = targetCampaign, amount = BigDecimal("75.00"), providerRef = "monerium:abc", confirmedAt = Instant.now())
            .setId(id)

    private fun makeRow(
        dId: UUID = donorId, name: String? = "Marie D.", anon: Boolean = false,
        total: BigDecimal = BigDecimal("150"), count: Long = 2,
    ) = object : DonorAggregateRow {
        override fun getDonorId() = dId
        override fun getDisplayName() = name
        override fun getAnonymous() = anon
        override fun getTotalAmount() = total
        override fun getTxCount() = count
        override fun getLastDonationAt(): Instant = Instant.now()
    }

    @Test
    fun `listDonors returns mapped page`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { donationRepository.findDonorAggregatesByCampaignId(campaignId, any()) } returns
            PageImpl(listOf(makeRow()))

        val result = service.listDonors(campaignId, userId, null, "amount", 0, 20)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].displayName).isEqualTo("Marie D.")
        assertThat(result.content[0].donorId).isEqualTo(donorId)
    }

    @Test
    fun `listDonors masks anonymous donor name`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { donationRepository.findDonorAggregatesByCampaignId(campaignId, any()) } returns
            PageImpl(listOf(makeRow(anon = true, name = "Real Name")))

        val result = service.listDonors(campaignId, userId, null, "amount", 0, 20)

        assertThat(result.content[0].displayName).isEqualTo("Anonyme")
    }

    @Test
    fun `listDonors with search delegates to search repo method`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { donationRepository.findDonorAggregatesByCampaignIdAndSearch(campaignId, "Marie", any<Pageable>()) } returns
            PageImpl(listOf(makeRow()))

        val result = service.listDonors(campaignId, userId, "Marie", "amount", 0, 20)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun `listDonors throws NotFoundException when campaign belongs to another association`() {
        val foreignAssoc    = AssociationProfile(user = assocUser, name = "Other", identifier = "999").setId(otherAssocId)
        val foreignCampaign = Campaign(association = foreignAssoc, name = "C", emoji = "🌍", description = "d", goal = BigDecimal("1000"), status = CampaignStatus.LIVE).setId(campaignId)
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(foreignCampaign)

        assertThrows<NotFoundException> {
            service.listDonors(campaignId, userId, null, null, 0, 20)
        }
    }

    @Test
    fun `listDonors throws NotFoundException when campaign does not exist`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.empty()

        assertThrows<NotFoundException> {
            service.listDonors(campaignId, userId, null, null, 0, 20)
        }
    }

    @Test
    fun `listDonorDonations returns mapped page`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { donationRepository.findByDonorIdAndCampaignId(donorId, campaignId, any()) } returns
            PageImpl(listOf(makeDonation()))

        val result = service.listDonorDonations(campaignId, donorId, userId, 0, 20)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].amount).isEqualByComparingTo("75.00")
        assertThat(result.content[0].onChain).isTrue()
    }

    @Test
    fun `getDonation returns dto`() {
        val donation = makeDonation()
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { donationRepository.findById(donationId) } returns Optional.of(donation)

        val result = service.getDonation(campaignId, donationId, userId)

        assertThat(result.id).isEqualTo(donationId)
        assertThat(result.providerRef).isEqualTo("monerium:abc")
    }

    @Test
    fun `getDonation throws NotFoundException when donation not found`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { donationRepository.findById(donationId) } returns Optional.empty()

        assertThrows<NotFoundException> {
            service.getDonation(campaignId, donationId, userId)
        }
    }

    @Test
    fun `getDonation throws NotFoundException when donation belongs to different campaign`() {
        val otherCampaignId = UUID.randomUUID()
        val otherCampaign   = Campaign(association = assoc, name = "Other", emoji = "🌍", description = "d", goal = BigDecimal("1000"), status = CampaignStatus.LIVE).setId(otherCampaignId)
        val donation        = makeDonation(targetCampaign = otherCampaign)

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { donationRepository.findById(donationId) } returns Optional.of(donation)

        assertThrows<NotFoundException> {
            service.getDonation(campaignId, donationId, userId)
        }
    }
}

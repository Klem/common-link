package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.dto.DonationAllocationDto
import org.commonlink.dto.JourneyStep
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Donation
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.OnchainJobRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorDonationJourneyServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository = mockk<DonationRepository>()
    private val onchainJobRepository = mockk<OnchainJobRepository>()
    private val donationAllocationService = mockk<DonationAllocationService>()

    private val readScope = DonorReadScope(donorProfileRepository, donationRepository)
    private val service = DonorDonationJourneyService(readScope, donationAllocationService, onchainJobRepository, donationRepository)

    // Distinct on purpose: donorId (DonorProfile PK) must never be conflated with userId (the JWT
    // subject) — that conflation is exactly the bug that shipped in this service (see DonorReadScope).
    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val donorId = UUID.randomUUID()
    private val otherDonorId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val donationId = UUID.randomUUID()

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val association = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789").setId(UUID.randomUUID())
    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
    private val donor = DonorProfile(user = donorUser).setId(donorId)
    private val otherDonorUser = User(email = "o@test.com", role = UserRole.DONOR, provider = AuthProvider.MAGIC_LINK)
    private val otherDonor = DonorProfile(user = otherDonorUser).setId(otherDonorId)

    init {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donorProfileRepository.findByUserId(otherUserId) } returns Optional.of(otherDonor)
    }

    private fun campaign(status: CampaignStatus = CampaignStatus.LIVE): Campaign =
        Campaign(association = association, name = "Camp", emoji = "🌍", goal = BigDecimal("10000"), status = status).setId(campaignId)

    private fun donation(campaign: Campaign, confirmedAt: Instant = Instant.parse("2026-01-01T00:00:00Z")): Donation =
        Donation(donor = donor, campaign = campaign, amount = BigDecimal("100"), providerRef = "mollie:tr_x", confirmedAt = confirmedAt)
            .setId(donationId)

    private fun noAllocation(): List<DonationAllocationDto> = emptyList()

    @Test
    fun `all 4 steps reached when onchain job is DONE, donation used, and campaign completed`() {
        val camp = campaign(CampaignStatus.COMPLETED)
        val d = donation(camp)
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey("DONATION:$donationId") } returns
            OnchainJob(action = OnchainJobAction.RECORD_DONATION, payloadJson = "{}", status = OnchainJobStatus.DONE)
        every { donationAllocationService.allocateCampaign(campaignId) } returns listOf(
            DonationAllocationDto(donationId, usedAmount = BigDecimal("100"), remainingAmount = BigDecimal.ZERO, fundedPayouts = emptyList())
        )
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(d), PageRequest.of(0, 1000), 1)

        val journey = service.getJourney(userId, donationId)

        assertThat(journey.steps).allSatisfy { assertThat(it.reached).isTrue() }
        assertThat(journey.steps.first { it.step == JourneyStep.RECEIVED }.reachedAt).isEqualTo(d.confirmedAt)
        assertThat(journey.usedAmount).isEqualByComparingTo("100")
        assertThat(journey.remainingAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `journey exposes the allocation's funded payouts for the traceability modal`() {
        val camp = campaign()
        val d = donation(camp)
        val payoutId = UUID.randomUUID()
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey("DONATION:$donationId") } returns null
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(d), PageRequest.of(0, 1000), 1)
        every { donationAllocationService.allocateCampaign(campaignId) } returns listOf(
            DonationAllocationDto(
                donationId, usedAmount = BigDecimal("40"), remainingAmount = BigDecimal("60"),
                fundedPayouts = listOf(
                    org.commonlink.dto.FundedPayoutShareDto(
                        payoutId, "Achat matériel", amountImputed = BigDecimal("40"),
                        confirmedAt = Instant.parse("2026-02-01T00:00:00Z"),
                    ),
                ),
            )
        )

        val journey = service.getJourney(userId, donationId)

        assertThat(journey.fundedPayouts).hasSize(1)
        assertThat(journey.fundedPayouts[0].payoutId).isEqualTo(payoutId)
        assertThat(journey.fundedPayouts[0].amountImputed).isEqualByComparingTo("40")
    }

    @Test
    fun `journey falls back to the full donation amount as remaining when the donation has no allocation entry yet`() {
        val camp = campaign()
        val d = donation(camp)
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey("DONATION:$donationId") } returns null
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(d), PageRequest.of(0, 1000), 1)
        every { donationAllocationService.allocateCampaign(campaignId) } returns noAllocation()

        val journey = service.getJourney(userId, donationId)

        assertThat(journey.usedAmount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(journey.remainingAmount).isEqualByComparingTo(d.amount)
        assertThat(journey.fundedPayouts).isEmpty()
    }

    @Test
    fun `RECORDED step is not reached when the onchain job is absent`() {
        val camp = campaign()
        val d = donation(camp)
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey("DONATION:$donationId") } returns null
        every { donationAllocationService.allocateCampaign(campaignId) } returns noAllocation()
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(d), PageRequest.of(0, 1000), 1)

        val journey = service.getJourney(userId, donationId)

        assertThat(journey.steps.first { it.step == JourneyStep.RECORDED }.reached).isFalse()
    }

    @Test
    fun `RECORDED step is not reached when the onchain job is PENDING or FAILED`() {
        val camp = campaign()
        val d = donation(camp)
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { donationAllocationService.allocateCampaign(campaignId) } returns noAllocation()
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(d), PageRequest.of(0, 1000), 1)

        for (status in listOf(OnchainJobStatus.PENDING, OnchainJobStatus.RUNNING, OnchainJobStatus.FAILED)) {
            every { onchainJobRepository.findByCorrelationKey("DONATION:$donationId") } returns
                OnchainJob(action = OnchainJobAction.RECORD_DONATION, payloadJson = "{}", status = status)

            val journey = service.getJourney(userId, donationId)
            assertThat(journey.steps.first { it.step == JourneyStep.RECORDED }.reached).isFalse()
        }
    }

    @Test
    fun `SPENT step is reached only when the FIFO allocation used more than zero`() {
        val camp = campaign()
        val d = donation(camp)
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey("DONATION:$donationId") } returns null
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(d), PageRequest.of(0, 1000), 1)

        every { donationAllocationService.allocateCampaign(campaignId) } returns listOf(
            DonationAllocationDto(donationId, usedAmount = BigDecimal.ZERO, remainingAmount = BigDecimal("100"), fundedPayouts = emptyList())
        )
        assertThat(service.getJourney(userId, donationId).steps.first { it.step == JourneyStep.SPENT }.reached).isFalse()

        every { donationAllocationService.allocateCampaign(campaignId) } returns listOf(
            DonationAllocationDto(donationId, usedAmount = BigDecimal("1"), remainingAmount = BigDecimal("99"), fundedPayouts = emptyList())
        )
        assertThat(service.getJourney(userId, donationId).steps.first { it.step == JourneyStep.SPENT }.reached).isTrue()
    }

    @Test
    fun `IMPACT_REPORTED step is reached only when the campaign is COMPLETED`() {
        val d = donation(campaign(CampaignStatus.LIVE))
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey("DONATION:$donationId") } returns null
        every { donationAllocationService.allocateCampaign(campaignId) } returns noAllocation()
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(d), PageRequest.of(0, 1000), 1)

        assertThat(service.getJourney(userId, donationId).steps.first { it.step == JourneyStep.IMPACT_REPORTED }.reached).isFalse()
    }

    @Test
    fun `navigation - donation at the head of the history has no more recent neighbour`() {
        val camp = campaign()
        val d = donation(camp, Instant.parse("2026-03-01T00:00:00Z"))
        val older = donation(camp, Instant.parse("2026-01-01T00:00:00Z")).setId(UUID.randomUUID())
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey(any()) } returns null
        every { donationAllocationService.allocateCampaign(campaignId) } returns noAllocation()
        // newest first: d is at index 0
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(d, older), PageRequest.of(0, 1000), 2)

        val journey = service.getJourney(userId, donationId)

        assertThat(journey.nextDonationId).isNull()
        assertThat(journey.previousDonationId).isEqualTo(older.id)
    }

    @Test
    fun `navigation - donation at the tail of the history has no older neighbour`() {
        val camp = campaign()
        val newer = donation(camp, Instant.parse("2026-03-01T00:00:00Z")).setId(UUID.randomUUID())
        val d = donation(camp, Instant.parse("2026-01-01T00:00:00Z"))
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey(any()) } returns null
        every { donationAllocationService.allocateCampaign(campaignId) } returns noAllocation()
        // newest first: newer at index 0, d (tail) at index 1
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(newer, d), PageRequest.of(0, 1000), 2)

        val journey = service.getJourney(userId, donationId)

        assertThat(journey.previousDonationId).isNull()
        assertThat(journey.nextDonationId).isEqualTo(newer.id)
    }

    @Test
    fun `navigation - donation in the middle of the history has both neighbours`() {
        val camp = campaign()
        val newest = donation(camp, Instant.parse("2026-03-01T00:00:00Z")).setId(UUID.randomUUID())
        val d = donation(camp, Instant.parse("2026-02-01T00:00:00Z"))
        val oldest = donation(camp, Instant.parse("2026-01-01T00:00:00Z")).setId(UUID.randomUUID())
        every { donationRepository.findById(donationId) } returns Optional.of(d)
        every { onchainJobRepository.findByCorrelationKey(any()) } returns null
        every { donationAllocationService.allocateCampaign(campaignId) } returns noAllocation()
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(newest, d, oldest), PageRequest.of(0, 1000), 3)

        val journey = service.getJourney(userId, donationId)

        assertThat(journey.previousDonationId).isEqualTo(oldest.id)
        assertThat(journey.nextDonationId).isEqualTo(newest.id)
    }

    @Test
    fun `getJourney refuses a donation owned by another donor`() {
        val camp = campaign()
        val d = donation(camp)
        every { donationRepository.findById(donationId) } returns Optional.of(d)

        assertThatThrownBy { service.getJourney(otherUserId, donationId) }
            .isInstanceOf(AccessDeniedException::class.java)
    }
}

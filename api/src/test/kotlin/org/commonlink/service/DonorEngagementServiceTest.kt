package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.DonorFeedItemType
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignMilestone
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.Payee
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorEngagementServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository = mockk<DonationRepository>()
    private val payoutRepository = mockk<PayoutRepository>()
    private val campaignMilestoneRepository = mockk<CampaignMilestoneRepository>()
    private val campaignRepository = mockk<CampaignRepository>()

    private val service = DonorEngagementService(
        DonorReadScope(donorProfileRepository, donationRepository),
        donorProfileRepository,
        payoutRepository,
        campaignMilestoneRepository,
        campaignRepository,
    )

    private val userId = UUID.randomUUID()
    private val donorId = UUID.randomUUID()

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, emailVerified = true)
    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL, emailVerified = true)
    private val association = AssociationProfile(user = assocUser, name = "Asso", identifier = "775671356")
    private val campaign = Campaign(association = association, name = "Camp", status = CampaignStatus.LIVE)
        .setId(UUID.randomUUID())
    private val payee = Payee(association = association, name = "Payee", identifier1 = "123456789")

    private fun donor(lastSeenAt: Instant?, notifyNewPayout: Boolean = true, notifyGoalReached: Boolean = true) =
        DonorProfile(
            user = donorUser,
            notifyNewPayout = notifyNewPayout,
            notifyGoalReached = notifyGoalReached,
        ).setId(donorId).also { it.lastSeenAt = lastSeenAt }

    private fun stubEmptySources() {
        every { payoutRepository.findConfirmedSinceForDonor(any(), any()) } returns emptyList()
        every { campaignMilestoneRepository.findReachedSinceForDonor(any(), any()) } returns emptyList()
        every { campaignRepository.findCompletedSinceForDonor(any(), any()) } returns emptyList()
    }

    private fun confirmedPayout(confirmedAt: Instant) = Payout(
        campaign = campaign,
        payee = payee,
        payeeIbanId = UUID.randomUUID(),
        payeeIbanValue = "FR7630006000011234567890189",
        amount = java.math.BigDecimal("100.00"),
        kind = PayoutKind.EXPENSE,
        typeCode = "60-mat",
        label = "Achat matériel",
        status = PayoutStatus.CONFIRMED,
    ).also { it.confirmedAt = confirmedAt }

    @Test
    fun `feed is empty for a donor with no history`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor(lastSeenAt = null))
        stubEmptySources()

        assertThat(service.getFeed(userId)).isEmpty()
    }

    @Test
    fun `first call with lastSeenAt null returns everything available`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor(lastSeenAt = null))
        every { payoutRepository.findConfirmedSinceForDonor(donorId, Instant.EPOCH) } returns listOf(confirmedPayout(Instant.now()))
        every { campaignMilestoneRepository.findReachedSinceForDonor(donorId, Instant.EPOCH) } returns emptyList()
        every { campaignRepository.findCompletedSinceForDonor(donorId, Instant.EPOCH) } returns emptyList()

        val feed = service.getFeed(userId)

        assertThat(feed).hasSize(1)
        assertThat(feed[0].type).isEqualTo(DonorFeedItemType.PAYOUT_CONFIRMED)
    }

    @Test
    fun `a subsequent call only returns events after the donor's last visit`() {
        val since = Instant.parse("2026-09-01T00:00:00Z")
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor(lastSeenAt = since))
        val sinceSlot = slot<Instant>()
        every { payoutRepository.findConfirmedSinceForDonor(donorId, capture(sinceSlot)) } returns emptyList()
        every { campaignMilestoneRepository.findReachedSinceForDonor(any(), any()) } returns emptyList()
        every { campaignRepository.findCompletedSinceForDonor(any(), any()) } returns emptyList()

        service.getFeed(userId)

        assertThat(sinceSlot.captured).isEqualTo(since)
    }

    @Test
    fun `notifyNewPayout = false excludes payout events but keeps milestones`() {
        every { donorProfileRepository.findByUserId(userId) } returns
            Optional.of(donor(lastSeenAt = null, notifyNewPayout = false))
        every { payoutRepository.findConfirmedSinceForDonor(any(), any()) } returns listOf(confirmedPayout(Instant.now()))
        val milestone = CampaignMilestone(campaign = campaign, title = "Palier 1").also { it.reachedAt = Instant.now() }
        every { campaignMilestoneRepository.findReachedSinceForDonor(any(), any()) } returns listOf(milestone)
        every { campaignRepository.findCompletedSinceForDonor(any(), any()) } returns emptyList()

        val feed = service.getFeed(userId)

        assertThat(feed).hasSize(1)
        assertThat(feed[0].type).isEqualTo(DonorFeedItemType.MILESTONE_REACHED)
        verify(exactly = 0) { payoutRepository.findConfirmedSinceForDonor(any(), any()) }
    }

    @Test
    fun `notifyGoalReached = false excludes milestones and campaign completions but keeps payouts`() {
        every { donorProfileRepository.findByUserId(userId) } returns
            Optional.of(donor(lastSeenAt = null, notifyGoalReached = false))
        every { payoutRepository.findConfirmedSinceForDonor(any(), any()) } returns listOf(confirmedPayout(Instant.now()))

        val feed = service.getFeed(userId)

        assertThat(feed).hasSize(1)
        assertThat(feed[0].type).isEqualTo(DonorFeedItemType.PAYOUT_CONFIRMED)
        verify(exactly = 0) { campaignMilestoneRepository.findReachedSinceForDonor(any(), any()) }
        verify(exactly = 0) { campaignRepository.findCompletedSinceForDonor(any(), any()) }
    }

    @Test
    fun `a payout item label uses the D2-validated wording, never claiming on-chain registration`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor(lastSeenAt = null))
        every { payoutRepository.findConfirmedSinceForDonor(any(), any()) } returns listOf(confirmedPayout(Instant.now()))
        every { campaignMilestoneRepository.findReachedSinceForDonor(any(), any()) } returns emptyList()
        every { campaignRepository.findCompletedSinceForDonor(any(), any()) } returns emptyList()

        val label = service.getFeed(userId).single().label

        assertThat(label).contains("Les dépenses sont tracées et vérifiées")
        assertThat(label).contains("inscription au registre public est en cours de déploiement")
        assertThat(label).doesNotContain("inscrit au registre public")
        assertThat(label).doesNotContain("inscrite au registre public")
    }

    @Test
    fun `markSeen sets lastSeenAt to now and persists it`() {
        val profile = donor(lastSeenAt = null)
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(profile)
        every { donorProfileRepository.save(any()) } answers { firstArg() }

        service.markSeen(userId)

        assertThat(profile.lastSeenAt).isNotNull()
        verify { donorProfileRepository.save(profile) }
    }
}

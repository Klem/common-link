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
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
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

/**
 * Unit tests for the donor read scope.
 *
 * Denial semantics under test: a missing row is a 404 ([NotFoundException]), an existing row owned
 * by somebody else is a 403 ([AccessDeniedException]). The two must never be swapped — a 404 on a
 * donation that exists would leak nothing, but a 403 on a donation that does not exist would
 * confirm its absence to a prober, and the inverse leaks ownership.
 */
class DonorReadScopeTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository     = mockk<DonationRepository>()
    private val scope = DonorReadScope(donorProfileRepository, donationRepository)

    private val userId      = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val donorId     = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherDonorId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val campaignId  = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val donationId  = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val assocId     = UUID.fromString("00000000-0000-0000-0000-000000000005")

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL, emailVerified = true)
    private val assoc     = AssociationProfile(user = assocUser, name = "Asso Test", identifier = "123456789").setId(assocId)
    private val campaign  = Campaign(association = assoc, name = "Camp", emoji = "🌍", goal = BigDecimal("1000"), status = CampaignStatus.LIVE).setId(campaignId)

    private val donorUser  = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, emailVerified = true)
    private val donor      = DonorProfile(user = donorUser, displayName = "Marie D.").setId(donorId)
    private val otherDonor = DonorProfile(user = donorUser, displayName = "Paul T.").setId(otherDonorId)

    private fun donation(owner: DonorProfile = donor) =
        Donation(donor = owner, campaign = campaign, amount = BigDecimal("50.00"), providerRef = "mollie:tr_a", confirmedAt = Instant.now())
            .setId(donationId)

    // ── resolve ───────────────────────────────────────────────────────────

    @Test
    fun `resolve returns the donor profile attached to the user`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)

        assertThat(scope.resolve(userId).id).isEqualTo(donorId)
    }

    @Test
    fun `resolve throws UserNotFoundException when no donor profile exists`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.empty()

        assertThrows<UserNotFoundException> { scope.resolve(userId) }
    }

    // ── assertHasDonatedTo ────────────────────────────────────────────────

    @Test
    fun `assertHasDonatedTo passes when the donor has a confirmed donation on the campaign`() {
        every { donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignId) } returns true

        scope.assertHasDonatedTo(donorId, campaignId)
    }

    @Test
    fun `assertHasDonatedTo denies a donor with no confirmed donation on the campaign`() {
        every { donationRepository.existsConfirmedByDonorIdAndCampaignId(donorId, campaignId) } returns false

        assertThrows<AccessDeniedException> { scope.assertHasDonatedTo(donorId, campaignId) }
    }

    // ── assertOwnsDonation ────────────────────────────────────────────────

    @Test
    fun `assertOwnsDonation returns the donation when it belongs to the donor`() {
        every { donationRepository.findById(donationId) } returns Optional.of(donation())

        assertThat(scope.assertOwnsDonation(donorId, donationId).id).isEqualTo(donationId)
    }

    @Test
    fun `assertOwnsDonation denies a donation owned by another donor`() {
        every { donationRepository.findById(donationId) } returns Optional.of(donation(owner = otherDonor))

        assertThrows<AccessDeniedException> { scope.assertOwnsDonation(donorId, donationId) }
    }

    @Test
    fun `assertOwnsDonation throws NotFoundException when the donation does not exist`() {
        every { donationRepository.findById(donationId) } returns Optional.empty()

        assertThrows<NotFoundException> { scope.assertOwnsDonation(donorId, donationId) }
    }
}

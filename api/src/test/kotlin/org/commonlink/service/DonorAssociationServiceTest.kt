package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonationRepository.AssociationCategoryRow
import org.commonlink.repository.DonationRepository.DonorAssociationRow
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.PayoutRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorAssociationServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val donationRepository     = mockk<DonationRepository>()
    private val payoutRepository       = mockk<PayoutRepository>()

    private val service = DonorAssociationService(
        DonorReadScope(donorProfileRepository, donationRepository), donationRepository, payoutRepository,
    )

    private val userId   = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val donorId  = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val assocAId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val assocBId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private val lastDonation = Instant.parse("2026-02-01T10:00:00Z")

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, emailVerified = true)
    private val donor     = DonorProfile(user = donorUser, displayName = "Marie D.").setId(donorId)

    private fun aggregate(
        assocId: UUID, name: String, total: String, campaigns: Long, at: Instant? = lastDonation,
    ) = object : DonorAssociationRow {
        override fun getAssociationId() = assocId
        override fun getName() = name
        override fun getTotalAmount(): BigDecimal = BigDecimal(total)
        override fun getCampaignCount() = campaigns
        override fun getLastDonationAt() = at
    }

    private fun categoryRow(assocId: UUID, category: String?) = object : AssociationCategoryRow {
        override fun getAssociationId() = assocId
        override fun getCategory() = category
    }

    @Test
    fun `listAssociations returns one entry per funded association with its payout record`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns listOf(
            aggregate(assocAId, "Alpha", "150.00", 2),
            aggregate(assocBId, "Beta", "200.00", 1),
        )
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns listOf(
            categoryRow(assocAId, "Education"),
            categoryRow(assocBId, "Environnement"),
        )
        every { payoutRepository.countConfirmedByAssociationId(assocAId) } returns 4
        every { payoutRepository.countConfirmedByAssociationId(assocBId) } returns 0

        val result = service.listAssociations(userId)

        assertThat(result).hasSize(2)
        with(result[0]) {
            assertThat(associationId).isEqualTo(assocAId)
            assertThat(name).isEqualTo("Alpha")
            assertThat(category).isEqualTo("Education")
            assertThat(totalDonated).isEqualByComparingTo("150.00")
            assertThat(campaignCount).isEqualTo(2)
            assertThat(publishedPayoutCount).isEqualTo(4)
            assertThat(lastDonationAt).isEqualTo(lastDonation)
        }
        assertThat(result[1].publishedPayoutCount).isZero()
    }

    @Test
    fun `listAssociations keeps the category of the most recently funded campaign`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns listOf(
            aggregate(assocAId, "Alpha", "150.00", 2),
        )
        // Rows arrive oldest first — the later one must win
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns listOf(
            categoryRow(assocAId, "Education"),
            categoryRow(assocAId, "Sante"),
        )
        every { payoutRepository.countConfirmedByAssociationId(assocAId) } returns 0

        assertThat(service.listAssociations(userId)[0].category).isEqualTo("Sante")
    }

    @Test
    fun `listAssociations tolerates a campaign with no category`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns listOf(
            aggregate(assocAId, "Alpha", "10.00", 1),
        )
        every { donationRepository.findAssociationCategoriesByDonorId(donorId) } returns listOf(
            categoryRow(assocAId, null),
        )
        every { payoutRepository.countConfirmedByAssociationId(assocAId) } returns 0

        assertThat(service.listAssociations(userId)[0].category).isNull()
    }

    @Test
    fun `listAssociations returns an empty list for a donor who has never given`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
        every { donationRepository.findAssociationAggregatesByDonorId(donorId) } returns emptyList()

        assertThat(service.listAssociations(userId)).isEmpty()
        // No aggregates: neither the category query nor the payout counts are worth issuing
        verify(exactly = 0) { donationRepository.findAssociationCategoriesByDonorId(any()) }
        verify(exactly = 0) { payoutRepository.countConfirmedByAssociationId(any()) }
    }
}

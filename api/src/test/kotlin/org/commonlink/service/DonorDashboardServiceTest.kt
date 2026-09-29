package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Donation
import org.commonlink.entity.DonationReceipt
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.MandateEligibility
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.DonationReceiptRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonationRepository.AssociationOptionRow
import org.commonlink.repository.DonationRepository.ReceiptedDonationRow
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.security.DonorReadScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorDashboardServiceTest {

    private val donorProfileRepository   = mockk<DonorProfileRepository>()
    private val donationRepository       = mockk<DonationRepository>()
    private val donationReceiptRepository = mockk<DonationReceiptRepository>()
    private val fiscalMandateRepository  = mockk<FiscalMandateRepository>()

    private val readScope      = DonorReadScope(donorProfileRepository, donationRepository)
    private val taxRateService = TaxRateService(fiscalMandateRepository)
    private val service = DonorDashboardService(
        readScope, donationRepository, donationReceiptRepository, fiscalMandateRepository, taxRateService,
    )

    private val userId     = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val donorId    = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val assocAId   = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val assocBId   = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val campaignId = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val donationId = UUID.fromString("00000000-0000-0000-0000-000000000005")

    private val in2024 = Instant.parse("2024-06-15T10:00:00Z")
    private val in2026 = Instant.parse("2026-02-01T10:00:00Z")

    private val donorUser  = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, emailVerified = true)
    private val donor      = DonorProfile(user = donorUser, displayName = "Marie D.").setId(donorId)
    private val otherDonor = DonorProfile(user = donorUser, displayName = "Paul T.").setId(UUID.randomUUID())

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL, emailVerified = true)
    private val assocA    = AssociationProfile(user = assocUser, name = "Alpha", identifier = "111111111").setId(assocAId)
    private val campaign  = Campaign(association = assocA, name = "Camp", emoji = "🌱", goal = BigDecimal("1000"), status = CampaignStatus.LIVE).setId(campaignId)

    private fun donation(owner: DonorProfile = donor, id: UUID = donationId) =
        Donation(donor = owner, campaign = campaign, amount = BigDecimal("100.00"), providerRef = "mollie:tr_a", confirmedAt = in2024)
            .setId(id)

    private fun receiptedRow(assocId: UUID, amount: String, at: Instant) = object : ReceiptedDonationRow {
        override fun getAssociationId() = assocId
        override fun getAmount(): BigDecimal = BigDecimal(amount)
        override fun getConfirmedAt(): Instant = at
    }

    private fun mandate(assocId: UUID, eligibility: MandateEligibility, signedAt: Instant, revokedAt: Instant? = null) =
        FiscalMandate(
            association = AssociationProfile(user = assocUser, name = "x", identifier = "1").setId(assocId),
            eligibility = eligibility,
            reference = "MND-${UUID.randomUUID()}",
            signedAt = signedAt,
            revokedAt = revokedAt,
        )

    private fun resolvesDonor() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(donor)
    }

    private fun noDonations() {
        every { donationRepository.sumConfirmedAmountByDonorId(donorId) } returns null
        every { donationRepository.countConfirmedByDonorId(donorId) } returns 0
        every { donationRepository.countDistinctAssociationsByDonorId(donorId) } returns 0
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    @Test
    fun `getStats returns donor totals and zero reduction when no donation carries a receipt`() {
        resolvesDonor()
        every { donationRepository.sumConfirmedAmountByDonorId(donorId) } returns BigDecimal("350.00")
        every { donationRepository.countConfirmedByDonorId(donorId) } returns 3
        every { donationRepository.countDistinctAssociationsByDonorId(donorId) } returns 2
        every { donationRepository.findReceiptedRowsByDonorId(donorId) } returns emptyList()

        val stats = service.getStats(userId)

        assertThat(stats.totalDonated).isEqualByComparingTo("350.00")
        assertThat(stats.donationCount).isEqualTo(3)
        assertThat(stats.associationCount).isEqualTo(2)
        // Three confirmed donations, none receipted: nothing may be claimed
        assertThat(stats.estimatedTaxReduction).isEqualByComparingTo("0.00")
    }

    @Test
    fun `getStats sums 66 and 75 percent rates line by line, never a global percentage`() {
        resolvesDonor()
        noDonations()
        every { donationRepository.findReceiptedRowsByDonorId(donorId) } returns listOf(
            receiptedRow(assocAId, "100.00", in2024),   // 66 % → 66.00
            receiptedRow(assocBId, "200.00", in2024),   // 75 % → 150.00
        )
        every { fiscalMandateRepository.findAllByAssociationId(assocAId) } returns
            listOf(mandate(assocAId, MandateEligibility.OIG_66, in2024.minus(365, ChronoUnit.DAYS)))
        every { fiscalMandateRepository.findAllByAssociationId(assocBId) } returns
            listOf(mandate(assocBId, MandateEligibility.OIG_75_COLUCHE, in2024.minus(365, ChronoUnit.DAYS)))

        assertThat(service.getStats(userId).estimatedTaxReduction).isEqualByComparingTo("216.00")
    }

    @Test
    fun `getStats applies the rate in force at the donation date even after the mandate was revoked`() {
        resolvesDonor()
        noDonations()
        every { donationRepository.findReceiptedRowsByDonorId(donorId) } returns listOf(
            receiptedRow(assocAId, "100.00", in2024),
        )
        // Coluche mandate active when the donation was made, revoked since
        every { fiscalMandateRepository.findAllByAssociationId(assocAId) } returns listOf(
            mandate(
                assocAId, MandateEligibility.OIG_75_COLUCHE,
                signedAt = in2024.minus(365, ChronoUnit.DAYS),
                revokedAt = in2024.plus(30, ChronoUnit.DAYS),
            )
        )

        // 75 % of the day, not the 66 % fallback that "today" would produce
        assertThat(service.getStats(userId).estimatedTaxReduction).isEqualByComparingTo("75.00")
    }

    @Test
    fun `getStats ignores a mandate signed after the donation`() {
        resolvesDonor()
        noDonations()
        every { donationRepository.findReceiptedRowsByDonorId(donorId) } returns listOf(
            receiptedRow(assocAId, "100.00", in2024),
        )
        every { fiscalMandateRepository.findAllByAssociationId(assocAId) } returns listOf(
            mandate(assocAId, MandateEligibility.OIG_75_COLUCHE, signedAt = in2026),
        )

        // No mandate was in force at the donation date → the 66 % fallback applies
        assertThat(service.getStats(userId).estimatedTaxReduction).isEqualByComparingTo("66.00")
    }

    // ── History and filters ───────────────────────────────────────────────

    @Test
    fun `listDonations maps rows and resolves receipt availability for the whole page`() {
        resolvesDonor()
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(donation()))
        every { donationReceiptRepository.findRefsByDonationIds(listOf(donationId)) } returns listOf(
            object : DonationReceiptRepository.ReceiptRefRow {
                override fun getDonationId() = donationId
                override fun getReceiptNumber() = "2024-0001"
            }
        )

        val page = service.listDonations(userId, null, null, 0, 20)

        assertThat(page.content).hasSize(1)
        with(page.content[0]) {
            assertThat(id).isEqualTo(donationId)
            assertThat(donatedAt).isEqualTo(in2024)
            assertThat(amount).isEqualByComparingTo("100.00")
            assertThat(campaignName).isEqualTo("Camp")
            assertThat(campaignEmoji).isEqualTo("🌱")
            assertThat(associationName).isEqualTo("Alpha")
            assertThat(receiptAvailable).isTrue()
            assertThat(receiptNumber).isEqualTo("2024-0001")
        }
    }

    @Test
    fun `listDonations marks a donation without receipt as unavailable`() {
        resolvesDonor()
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(listOf(donation()))
        every { donationReceiptRepository.findRefsByDonationIds(listOf(donationId)) } returns emptyList()

        val row = service.listDonations(userId, null, null, 0, 20).content[0]

        assertThat(row.receiptAvailable).isFalse()
        assertThat(row.receiptNumber).isNull()
    }

    @Test
    fun `listDonations issues no receipt query on an empty page`() {
        resolvesDonor()
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, any()) } returns
            PageImpl(emptyList(), Pageable.ofSize(20), 0)

        assertThat(service.listDonations(userId, null, null, 0, 20).content).isEmpty()
        verify(exactly = 0) { donationReceiptRepository.findRefsByDonationIds(any()) }
    }

    @Test
    fun `listDonations forwards both filters to the query, combined`() {
        resolvesDonor()
        every { donationRepository.findByDonorIdFiltered(donorId, assocAId, 2024, any()) } returns
            PageImpl(emptyList(), Pageable.ofSize(20), 0)

        service.listDonations(userId, assocAId, 2024, 0, 20)

        verify { donationRepository.findByDonorIdFiltered(donorId, assocAId, 2024, any()) }
    }

    @Test
    fun `listDonations passes page and size through without imposing a sort`() {
        resolvesDonor()
        val pageable = slot<org.springframework.data.domain.Pageable>()
        every { donationRepository.findByDonorIdFiltered(donorId, null, null, capture(pageable)) } returns
            PageImpl(emptyList(), Pageable.ofSize(20), 0)

        service.listDonations(userId, null, null, 2, 50)

        assertThat(pageable.captured.pageNumber).isEqualTo(2)
        assertThat(pageable.captured.pageSize).isEqualTo(50)
        // The ordering belongs to the query; a Sort here would append a second ORDER BY
        assertThat(pageable.captured.sort.isSorted).isFalse()
    }

    @Test
    fun `listDonations rejects paging and year values outside their range`() {
        resolvesDonor()

        assertThatThrownBy { service.listDonations(userId, null, null, -1, 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.listDonations(userId, null, null, 0, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.listDonations(userId, null, null, 0, 101) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.listDonations(userId, null, 1999, 0, 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.listDonations(userId, null, 2999, 0, 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getFilters returns the associations funded and the years with donations`() {
        resolvesDonor()
        every { donationRepository.findDistinctAssociationsByDonorId(donorId) } returns listOf(
            object : AssociationOptionRow {
                override fun getId() = assocAId
                override fun getName() = "Alpha"
            }
        )
        every { donationRepository.findDistinctYearsByDonorId(donorId) } returns listOf(2026, 2024)

        val filters = service.getFilters(userId)

        assertThat(filters.associations).singleElement()
            .satisfies({ assertThat(it.id).isEqualTo(assocAId); assertThat(it.name).isEqualTo("Alpha") })
        assertThat(filters.years).containsExactly(2026, 2024)
    }

    // ── Receipt ───────────────────────────────────────────────────────────

    @Test
    fun `getReceipt returns the stored PDF of the donor's own donation`() {
        resolvesDonor()
        val donation = donation()
        every { donationRepository.findById(donationId) } returns Optional.of(donation)
        every { donationReceiptRepository.findByDonationId(donationId) } returns DonationReceipt(
            donation = donation, receiptNumber = "2024-0001", pdfBytes = byteArrayOf(1, 2), generatedAt = in2024,
        )

        val download = service.getReceipt(userId, donationId)

        assertThat(download.fileName).isEqualTo("recu-2024-0001.pdf")
        assertThat(download.pdfBytes).containsExactly(1, 2)
    }

    @Test
    fun `getReceipt refuses a donation owned by another donor`() {
        resolvesDonor()
        every { donationRepository.findById(donationId) } returns Optional.of(donation(owner = otherDonor))

        assertThrows<AccessDeniedException> { service.getReceipt(userId, donationId) }
        // Ownership is checked first: the receipt table is never even consulted
        verify(exactly = 0) { donationReceiptRepository.findByDonationId(any()) }
    }

    @Test
    fun `getReceipt returns 404 when the donation has no receipt yet`() {
        resolvesDonor()
        every { donationRepository.findById(donationId) } returns Optional.of(donation())
        every { donationReceiptRepository.findByDonationId(donationId) } returns null

        assertThrows<NotFoundException> { service.getReceipt(userId, donationId) }
    }
}

package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.CreatePayoutRequest
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.Payee
import org.commonlink.entity.PayeeIban
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayeeIbanRepository
import org.commonlink.repository.PayeeRepository
import org.commonlink.repository.PayoutRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class PayoutServiceTest {

    private val payoutRepository              = mockk<PayoutRepository>()
    private val campaignRepository            = mockk<CampaignRepository>()
    private val associationProfileRepository  = mockk<AssociationProfileRepository>()
    private val payeeRepository               = mockk<PayeeRepository>()
    private val payeeIbanRepository           = mockk<PayeeIbanRepository>()
    private val donationRepository            = mockk<DonationRepository>()
    private val confirmer                     = mockk<PayoutConfirmer>()

    private val service = PayoutService(
        payoutRepository, campaignRepository, associationProfileRepository,
        payeeRepository, payeeIbanRepository, donationRepository, confirmer
    )

    private val userId     = UUID.randomUUID()   // JWT subject (User.id)
    private val assocId    = UUID.randomUUID()   // AssociationProfile.id
    private val campaignId = UUID.randomUUID()
    private val payeeId   = UUID.randomUUID()
    private val ibanId    = UUID.randomUUID()
    private val payoutId  = UUID.randomUUID()

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private val assoc     = AssociationProfile(user = assocUser, name = "Asso", identifier = "123456789")
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, assocId) }

    private val campaign  = Campaign(association = assoc, name = "Camp", emoji = "🌍", description = "desc", goal = BigDecimal("10000"), status = CampaignStatus.LIVE)
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, campaignId) }

    private val payee = Payee(association = assoc, name = "Payee", identifier1 = "123456789")
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, payeeId) }

    private val payeeIban = PayeeIban(payee = payee, iban = "FR7630006000011234567890189", status = IbanVerificationStatus.VERIFIED)
        .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, ibanId) }

    private val pendingPayout = Payout(campaign = campaign, payee = payee, payeeIban = payeeIban,
        amount = BigDecimal("500"), kind = PayoutKind.EXPENSE, typeCode = "60-mat", label = "Achat matériel", status = PayoutStatus.PENDING)

    @Test
    fun `create - happy path returns PayoutDto`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(payeeIban)
        every { payoutRepository.save(any()) } returnsArgument 0

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Achat matériel pédagogique")
        val result = service.create(campaignId, request, userId)

        assertThat(result.amount).isEqualByComparingTo("500")
        assertThat(result.status).isEqualTo(PayoutStatus.PENDING)
    }

    @Test
    fun `create - wrong association returns NotFoundException`() {
        val wrongUserId  = UUID.randomUUID()
        val wrongAssocId = UUID.randomUUID()
        val wrongAssoc   = AssociationProfile(user = assocUser, name = "Other", identifier = "999999999")
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, wrongAssocId) }
        every { associationProfileRepository.findByUserId(wrongUserId) } returns Optional.of(wrongAssoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Motif test create wrong")
        assertThrows<NotFoundException> { service.create(campaignId, request, wrongUserId) }
    }

    @Test
    fun `create - IBAN does not belong to payee throws NotFoundException`() {
        val otherPayee = Payee(association = assoc, name = "Other", identifier1 = "987654321")
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, UUID.randomUUID()) }
        val ibanFromOtherPayee = PayeeIban(payee = otherPayee, iban = "DE89370400440532013000")
            .also { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, ibanId) }

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { payeeRepository.findById(payeeId) } returns Optional.of(payee)
        every { payeeIbanRepository.findById(ibanId) } returns Optional.of(ibanFromOtherPayee)

        val request = CreatePayoutRequest(payeeId, ibanId, BigDecimal("500"), PayoutKind.EXPENSE, "60-mat", "Motif test iban mismatch")
        assertThrows<NotFoundException> { service.create(campaignId, request, userId) }
    }

    @Test
    fun `confirm - transitions PENDING to CONFIRMED via confirmer`() {
        val confirmedPayout = Payout(campaign = campaign, payee = payee, payeeIban = payeeIban,
            amount = BigDecimal("500"), kind = PayoutKind.EXPENSE, typeCode = "60-mat", label = "Achat matériel", status = PayoutStatus.CONFIRMED)

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payoutId, assocId) } returns pendingPayout
        every { confirmer.confirmAndEnqueue(pendingPayout) } returns confirmedPayout

        val result = service.confirm(campaignId, payoutId, userId)

        assertThat(result.status).isEqualTo(PayoutStatus.CONFIRMED)
        verify { confirmer.confirmAndEnqueue(pendingPayout) }
    }

    @Test
    fun `confirm - already CONFIRMED throws ConflictException`() {
        val alreadyConfirmed = Payout(campaign = campaign, payee = payee, payeeIban = payeeIban,
            amount = BigDecimal("500"), kind = PayoutKind.EXPENSE, typeCode = "60-mat", label = "Done", status = PayoutStatus.CONFIRMED)

        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payoutId, assocId) } returns alreadyConfirmed

        assertThrows<ConflictException> { service.confirm(campaignId, payoutId, userId) }
    }

    @Test
    fun `confirm - not found throws NotFoundException`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payoutId, assocId) } returns null

        assertThrows<NotFoundException> { service.confirm(campaignId, payoutId, userId) }
    }

    @Test
    fun `getSummary - returns correct aggregates`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(assoc)
        every { campaignRepository.findById(campaignId) } returns Optional.of(campaign)
        every { payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED) } returns BigDecimal("1000")
        every { payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.PENDING) } returns BigDecimal("200")
        every { payoutRepository.countByCampaignId(campaignId) } returns 5L
        every { payoutRepository.countByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED) } returns 3L
        every { donationRepository.sumConfirmedAmountByCampaignId(campaignId) } returns BigDecimal("5000")

        val summary = service.getSummary(campaignId, userId)

        assertThat(summary.confirmedAmount).isEqualByComparingTo("1000")
        assertThat(summary.pendingAmount).isEqualByComparingTo("200")
        assertThat(summary.txTotal).isEqualTo(5L)
        assertThat(summary.availableBalance).isEqualByComparingTo("4000") // 5000 - 1000
    }
}

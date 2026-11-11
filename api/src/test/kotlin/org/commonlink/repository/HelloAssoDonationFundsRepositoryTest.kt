package org.commonlink.repository

import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoDonationFunds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Queries of [HelloAssoDonationFundsRepository] (WITHDRAWN exclusion of the available sum, not-paid-out total, row
 * locks) and of [HelloAssoCashOutRepository], on real PostgreSQL (V92, V93).
 */
@Transactional
class HelloAssoDonationFundsRepositoryTest : RealPostgresTest() {

    @Autowired private lateinit var fundsRepository: HelloAssoDonationFundsRepository
    @Autowired private lateinit var syncRepository: HelloAssoCashOutSyncRepository
    @Autowired private lateinit var cashOutRepository: HelloAssoCashOutRepository
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var entityManager: jakarta.persistence.EntityManager

    private lateinit var donor: DonorProfile
    private lateinit var campaignA: Campaign
    private lateinit var campaignOther: Campaign
    private lateinit var associationA: UUID

    @BeforeEach
    fun setup() {
        val tag = UUID.randomUUID().toString().take(8)
        donor = donorProfileRepository.save(TestFixtures.donorProfile(userRepository.save(TestFixtures.donorUser(email = "d-$tag@example.com"))))
        val a = associationProfileRepository.save(
            TestFixtures.associationProfile(userRepository.save(TestFixtures.associationUser(email = "a-$tag@example.com")), identifier = "1${tag.hashCode().mod(100_000_000).toString().padStart(8, '0')}"),
        )
        val b = associationProfileRepository.save(
            TestFixtures.associationProfile(userRepository.save(TestFixtures.associationUser(email = "b-$tag@example.com")), identifier = "2${tag.hashCode().mod(100_000_000).toString().padStart(8, '0')}"),
        )
        associationA = a.id!!
        campaignA = campaignRepository.save(TestFixtures.campaign(a, status = CampaignStatus.LIVE))
        campaignOther = campaignRepository.save(TestFixtures.campaign(b, status = CampaignStatus.LIVE))
    }

    private fun tracked(
        campaign: Campaign,
        state: FundsState,
        amount: String = "10.00",
        availableAt: Instant? = null,
        idCashOut: Long? = null,
    ): HelloAssoDonationFunds {
        val donation = donationRepository.save(
            TestFixtures.donation(donor, campaign, amount = BigDecimal(amount), providerRef = "helloasso:${UUID.randomUUID()}")
                .also { it.fundsAvailableAt = availableAt },
        )
        return fundsRepository.save(
            HelloAssoDonationFunds(donation = donation, haPaymentId = 70_000L + amount.hashCode(), fundsState = state, idCashOut = idCashOut),
        )
    }

    @Test
    fun `trackable rows exclude AVAILABLE and WITHDRAWN, and can be scoped to one association`() {
        val atProvider = tracked(campaignA, FundsState.AT_PROVIDER)
        val preparing = tracked(campaignA, FundsState.PAYOUT_PREPARING)
        val inTransit = tracked(campaignA, FundsState.IN_TRANSIT)
        tracked(campaignA, FundsState.AVAILABLE, availableAt = Instant.now())
        tracked(campaignA, FundsState.WITHDRAWN)
        val other = tracked(campaignOther, FundsState.AT_PROVIDER)
        entityManager.flush()
        entityManager.clear()

        val all = fundsRepository.findTrackable().map { it.donationId }
        assertEquals(setOf(atProvider.donationId, preparing.donationId, inTransit.donationId, other.donationId), all.toSet())
        assertEquals(associationA, fundsRepository.findTrackable().first { it.donationId == atProvider.donationId }.donation.campaign.association.id)

        val scoped = fundsRepository.findTrackableByAssociationId(associationA).map { it.donationId }
        assertEquals(setOf(atProvider.donationId, preparing.donationId, inTransit.donationId), scoped.toSet())
        assertTrue(fundsRepository.existsTrackable())
    }

    @Test
    fun `existsTrackable is false when every row is terminal`() {
        fundsRepository.deleteAllInBatch()
        tracked(campaignA, FundsState.AVAILABLE, availableAt = Instant.now())
        tracked(campaignA, FundsState.WITHDRAWN)
        entityManager.flush()

        assertFalse(fundsRepository.existsTrackable())
    }

    @Test
    fun `rows of one payout are found per association only`() {
        val first = tracked(campaignA, FundsState.IN_TRANSIT, idCashOut = 60001)
        val second = tracked(campaignA, FundsState.IN_TRANSIT, idCashOut = 60001)
        tracked(campaignA, FundsState.IN_TRANSIT, idCashOut = 60002)
        tracked(campaignOther, FundsState.IN_TRANSIT, idCashOut = 60001)
        entityManager.flush()
        entityManager.clear()

        val rows = fundsRepository.findByAssociationIdAndIdCashOut(associationA, 60001).map { it.donationId }
        assertEquals(setOf(first.donationId, second.donationId), rows.toSet())
    }

    @Test
    fun `a row is loaded under lock by donation id`() {
        val row = tracked(campaignA, FundsState.AT_PROVIDER)
        entityManager.flush()
        entityManager.clear()

        val locked = fundsRepository.findByDonationIdForUpdate(row.donationId!!)
        assertNotNull(locked)
        assertEquals(FundsState.AT_PROVIDER, locked!!.fundsState)
    }

    @Test
    fun `the available sum excludes a donation withdrawn after it became available`() {
        val at = Instant.now().minus(1, ChronoUnit.HOURS)
        donationRepository.save(
            TestFixtures.donation(donor, campaignA, amount = BigDecimal("40.00"), providerRef = "mollie:tr_${UUID.randomUUID()}")
                .also { it.fundsAvailableAt = at },
        )
        tracked(campaignA, FundsState.AVAILABLE, amount = "25.00", availableAt = at)
        tracked(campaignA, FundsState.WITHDRAWN, amount = "7.00", availableAt = at)
        tracked(campaignA, FundsState.IN_TRANSIT, amount = "100.00")
        entityManager.flush()

        assertEquals(0, BigDecimal("65.00").compareTo(donationRepository.sumAvailableAmountByCampaignId(campaignA.id!!)))
        // The FIFO keeps the withdrawn donation: payouts it funded stay explained.
        assertEquals(
            3,
            donationRepository.findByCampaignIdAndFundsAvailableAtIsNotNullOrderByFundsAvailableAtAscConfirmedAtAscIdAsc(campaignA.id!!).size,
        )
    }

    @Test
    fun `the sync row is inserted once and the refresh claim passes once per interval`() {
        assertEquals(1, syncRepository.insertIfMissing(associationA))
        assertEquals(0, syncRepository.insertIfMissing(associationA))

        val now = Instant.now()
        assertEquals(1, syncRepository.claimRefresh(associationA, now, now.minus(15, ChronoUnit.MINUTES)))
        assertEquals(0, syncRepository.claimRefresh(associationA, now.plusSeconds(60), now.plusSeconds(60).minus(15, ChronoUnit.MINUTES)))
        assertEquals(1, syncRepository.claimRefresh(associationA, now.plus(16, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.MINUTES)))
    }

    @Test
    fun `not paid out = AT_PROVIDER and PAYOUT_PREPARING of the association only, every campaign`() {
        assertEquals(NotPaidOutTotal(0, null), fundsRepository.sumNotPaidOutByAssociationId(associationA))

        val campaignA2 = campaignRepository.save(TestFixtures.campaign(campaignA.association, status = CampaignStatus.LIVE))
        tracked(campaignA, FundsState.AT_PROVIDER, amount = "10.00")
        tracked(campaignA2, FundsState.PAYOUT_PREPARING, amount = "5.50")
        tracked(campaignA, FundsState.IN_TRANSIT, amount = "100.00")
        tracked(campaignA, FundsState.AVAILABLE, amount = "200.00", availableAt = Instant.now())
        tracked(campaignA, FundsState.WITHDRAWN, amount = "300.00")
        tracked(campaignOther, FundsState.AT_PROVIDER, amount = "400.00")
        entityManager.flush()

        val total = fundsRepository.sumNotPaidOutByAssociationId(associationA)
        assertEquals(2, total.count)
        assertEquals(0, BigDecimal("15.50").compareTo(total.amount))
    }

    @Test
    fun `rows of a payout are found per association, and locked in id order with their state read under the lock`() {
        val a = tracked(campaignA, FundsState.IN_TRANSIT, idCashOut = 60001)
        val b = tracked(campaignA, FundsState.IN_TRANSIT, idCashOut = 60001)
        tracked(campaignOther, FundsState.IN_TRANSIT, idCashOut = 60001)
        entityManager.flush()

        assertEquals(setOf(a.donationId, b.donationId), fundsRepository.findByAssociationIdAndIdCashOut(associationA, 60001).map { it.donationId }.toSet())

        // A stale managed instance is refreshed by the lock.
        entityManager.createNativeQuery("UPDATE helloasso_donation_funds SET funds_state = 'AVAILABLE' WHERE donation_id = :id")
            .setParameter("id", a.donationId).executeUpdate()
        val locked = fundsRepository.lockAllForUpdate(listOf(b.donationId!!, a.donationId!!, UUID.randomUUID()))
        assertEquals(listOf(a.donationId, b.donationId).sortedBy { it }, locked.map { it.donationId })
        assertEquals(FundsState.AVAILABLE, locked.first { it.donationId == a.donationId }.fundsState)
    }

    @Test
    fun `a payout total is stored once, the first value kept`() {
        assertEquals(1, cashOutRepository.insertIfMissing(associationA, 60001, BigDecimal("30.00"), Instant.now()))
        assertEquals(0, cashOutRepository.insertIfMissing(associationA, 60001, BigDecimal("99.00"), Instant.now()))
        entityManager.clear()

        assertEquals(0, BigDecimal("30.00").compareTo(cashOutRepository.findByAssociationIdAndIdCashOut(associationA, 60001)!!.netTotal))
        assertEquals(null, cashOutRepository.findByAssociationIdAndIdCashOut(associationA, 60002))
    }
}

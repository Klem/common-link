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

/** Queries of [HelloAssoDonationFundsRepository] and the WITHDRAWN exclusion of the available sum, on real PostgreSQL (V92). */
@Transactional
class HelloAssoDonationFundsRepositoryTest : RealPostgresTest() {

    @Autowired private lateinit var fundsRepository: HelloAssoDonationFundsRepository
    @Autowired private lateinit var syncRepository: HelloAssoCashOutSyncRepository
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
}

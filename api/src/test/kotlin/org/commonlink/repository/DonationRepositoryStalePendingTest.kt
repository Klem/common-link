package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.DonorProfile
import org.commonlink.payment.PaymentProviderId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Pins the candidate set of the payment reconcilers ([DonationRepository.findStalePendingByProviderRefPrefix])
 * against real PostgreSQL: selection is by `providerRef` prefix only, never by the association's current provider.
 */
@ImportTestcontainers(TestcontainersConfig::class)
class DonationRepositoryStalePendingTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val donorProfileRepository: DonorProfileRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val donationRepository: DonationRepository,
) : AbstractRepositoryTest() {

    private val threshold = Instant.now().minus(15, ChronoUnit.MINUTES)
    private val stale = Instant.now().minus(30, ChronoUnit.MINUTES)

    private lateinit var donor: DonorProfile
    private lateinit var campaign: Campaign

    @BeforeEach
    fun setup() {
        val assocUser = userRepository.save(TestFixtures.associationUser(email = "stale-assoc-${System.nanoTime()}@test.com"))
        // Moved to HELLOASSO: its in-flight Mollie payments must still be reconciled by prefix.
        val assoc = associationProfileRepository.save(
            TestFixtures.associationProfile(assocUser).apply { paymentProvider = PaymentProviderId.HELLOASSO }
        )
        campaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))
        val donorUser = userRepository.save(TestFixtures.donorUser(email = "stale-donor-${System.nanoTime()}@test.com"))
        donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
    }

    private fun donation(providerRef: String, confirmedAt: Instant? = null, createdAt: Instant = stale) =
        donationRepository.save(
            TestFixtures.donation(donor, campaign, providerRef = providerRef, confirmedAt = confirmedAt, createdAt = createdAt)
        ).providerRef

    private fun staleMollieRefs() =
        donationRepository.findStalePendingByProviderRefPrefix(PaymentProviderId.MOLLIE.refPrefix, threshold)
            .map { it.providerRef }

    @Test
    fun `returns a stale mollie donation of an association moved to HELLOASSO`() {
        val ref = donation("mollie:tr_moved")

        assertThat(staleMollieRefs()).contains(ref)
    }

    @Test
    fun `excludes a stale helloasso donation`() {
        val ref = donation("helloasso:123")

        assertThat(staleMollieRefs()).doesNotContain(ref)
    }

    @Test
    fun `excludes a confirmed mollie donation`() {
        val ref = donation("mollie:tr_confirmed", confirmedAt = Instant.now())

        assertThat(staleMollieRefs()).doesNotContain(ref)
    }

    @Test
    fun `excludes a mollie donation created after the threshold`() {
        val ref = donation("mollie:tr_recent", createdAt = Instant.now())

        assertThat(staleMollieRefs()).doesNotContain(ref)
    }
}

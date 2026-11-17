package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.entity.CampaignStatus
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.RealPostgresTest
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID

/**
 * Proves on a real PostgreSQL that a Mollie payment confirmed by one caller is not confirmed again by a caller holding
 * a stale copy of the donation (audit 2026-11-12, H4).
 *
 * The webhook, the return-page status poll and the reconciler all reach [MollieWebhookService.handleWebhook]. The
 * scenario is made deterministic: the outer transaction (an open-in-view request) loads the donation unconfirmed,
 * another thread then confirms the payment and commits, and the outer request finally processes the same payment.
 * Before the fix the outer call re-read its own stale instance, passed the `confirmedAt` guards and ran a second
 * `raised = raised + amount`.
 *
 * Not transactional itself: each step commits as in production.
 */
class MollieWebhookConcurrencyTest : RealPostgresTest() {

    @Autowired private lateinit var mollieWebhookService: MollieWebhookService
    @Autowired private lateinit var transactionTemplate: TransactionTemplate
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var donationRepository: DonationRepository

    @MockkBean private lateinit var mollieClient: MollieClient
    @MockkBean private lateinit var mollieConnectTokenManager: MollieConnectTokenManager

    @Test
    fun `a payment confirmed meanwhile is not credited again by a caller holding a stale donation`() {
        val mollieId = "tr_stale_${UUID.randomUUID()}"
        val providerRef = "mollie:$mollieId"
        val campaignId = pendingDonation(providerRef)
        every { mollieConnectTokenManager.getValidAccessToken(any()) } returns "test_assoc_token"
        every { mollieClient.getPayment(mollieId, any()) } returns MolliePayment(
            id = mollieId,
            status = MolliePaymentStatus.PAID,
            amount = BigDecimal("50.00"),
            checkoutUrl = null,
            metadata = emptyMap(),
        )

        transactionTemplate.executeWithoutResult {
            // The request already holds the donation, unconfirmed, in its persistence context.
            assertNull(donationRepository.findByProviderRef(providerRef)!!.confirmedAt)

            // Meanwhile another caller confirms the payment and commits.
            val other = Thread { mollieWebhookService.handleWebhook(mollieId) }
            other.start()
            other.join()

            mollieWebhookService.handleWebhook(mollieId)
        }

        assertNotNull(donationRepository.findByProviderRef(providerRef)!!.confirmedAt)
        val raised = campaignRepository.findById(campaignId).orElseThrow().raised
        assertEquals(0, BigDecimal("50.00").compareTo(raised), "campaign credited once, got $raised")
    }

    /** Creates a LIVE campaign with nothing raised and one unconfirmed 50.00 donation; returns the campaign id. */
    private fun pendingDonation(providerRef: String): UUID {
        val donorUser = userRepository.save(TestFixtures.donorUser(email = "stale-donor-${System.nanoTime()}@example.com"))
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        val assocUser = userRepository.save(TestFixtures.associationUser(email = "stale-assoc-${System.nanoTime()}@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))
        donationRepository.save(
            TestFixtures.donation(donor = donor, campaign = campaign, providerRef = providerRef, confirmedAt = null),
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(campaign.raised))
        return campaign.id!!
    }
}

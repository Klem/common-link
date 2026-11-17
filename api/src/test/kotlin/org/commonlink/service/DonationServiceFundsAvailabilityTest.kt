package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.DonorProfile
import org.commonlink.onchain.DonorAddressGenerator
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

/**
 * [Donation.fundsAvailableAt] at confirmation: the confirmation instant for every provider but HelloAsso, whose money
 * stays at the provider until paid out to the association.
 */
class DonationServiceFundsAvailabilityTest {

    private val donationRepository = mockk<DonationRepository>(relaxed = true) {
        every { save(any<Donation>()) } answers { firstArg() }
    }
    private val donorProfileRepository = mockk<DonorProfileRepository>(relaxed = true)
    private val campaignRepository = mockk<CampaignRepository> {
        every { addToRaised(any(), any()) } returns 1
    }
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = DonationService(
        donationRepository, donorProfileRepository, campaignRepository, mockk<DonorAddressGenerator>(), publisher,
    )

    private fun donation(providerRef: String, confirmedAt: Instant? = null): Donation {
        val donor = mockk<DonorProfile>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { walletAddress } returns "0xabc"
        }
        val campaign = mockk<Campaign>(relaxed = true) { every { id } returns UUID.randomUUID() }
        val donation = Donation(
            id = UUID.randomUUID(), donor = donor, campaign = campaign, amount = BigDecimal("10.00"),
            providerRef = providerRef, confirmedAt = confirmedAt, createdAt = Instant.now().minusSeconds(600),
        )
        every { donationRepository.findById(donation.id!!) } returns Optional.of(donation)
        return donation
    }

    @Test
    fun `a Mollie donation is available at its confirmation instant`() {
        val donation = donation("mollie:tr_1")

        service.confirmDonation(donation.id!!)

        assertNotNull(donation.confirmedAt)
        assertEquals(donation.confirmedAt, donation.fundsAvailableAt)
    }

    @Test
    fun `a Mollie donation dated at the provider payment time is available at that time`() {
        val donation = donation("mollie:tr_2")
        val paidAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)

        service.confirmDonation(donation.id!!, paidAt)

        assertEquals(paidAt, donation.confirmedAt)
        assertEquals(paidAt, donation.fundsAvailableAt)
    }

    @Test
    fun `a HelloAsso donation is confirmed but not available`() {
        val donation = donation("helloasso:4242")

        service.confirmDonation(donation.id!!)

        assertNotNull(donation.confirmedAt)
        assertNull(donation.fundsAvailableAt)
    }

    @Test
    fun `an already confirmed donation is left unchanged`() {
        val confirmedAt = Instant.now().minusSeconds(3600)
        val donation = donation("mollie:tr_3", confirmedAt)

        service.confirmDonation(donation.id!!)

        assertEquals(confirmedAt, donation.confirmedAt)
        assertNull(donation.fundsAvailableAt)
        verify(exactly = 0) { donationRepository.save(any()) }
    }
}

package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.Donation
import org.commonlink.entity.DonationReceipt
import org.commonlink.repository.DonationReceiptRepository
import org.commonlink.repository.DonationRepository
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * [DonationReceiptService.sendReceiptEmailIfNeeded]: the guest sign-up invitation is passed only to guest
 * donors, and its URL never carries the donor's e-mail address.
 */
class DonationReceiptEmailTest {

    private val donationReceiptRepository = mockk<DonationReceiptRepository>()
    private val emailService = mockk<EmailService>()
    private val service = DonationReceiptService(
        donationRepository = mockk<DonationRepository>(),
        donationReceiptRepository = donationReceiptRepository,
        receiptService = mockk(),
        receiptNumberService = mockk(),
        emailService = emailService,
        outbox = mockk(),
        frontendUrl = "https://app.example.org",
    )

    private val donationId: UUID = UUID.randomUUID()

    private fun receiptFor(guest: Boolean): DonationReceipt {
        val donation = mockk<Donation>(relaxed = true).also {
            every { it.donor.user.email } returns "donor@example.com"
            every { it.donor.user.guest } returns guest
            every { it.donorFullName } returns "Jean Dupont"
            every { it.campaign.association.name } returns "Asso"
        }
        val receipt = mockk<DonationReceipt>(relaxed = true).also {
            every { it.donation } returns donation
            every { it.emailedAt } returns null
            every { it.receiptNumber } returns "2026-0001"
            every { it.pdfBytes } returns byteArrayOf(1)
        }
        every { donationReceiptRepository.findByDonationId(donationId) } returns receipt
        every { donationReceiptRepository.save(receipt) } returns receipt
        justRun { emailService.sendDonationReceipt(any(), any(), any(), any(), any(), any()) }
        return receipt
    }

    @Test
    fun `a guest donor gets the sign-up invitation URL`() {
        receiptFor(guest = true)

        service.sendReceiptEmailIfNeeded(donationId)

        verify(exactly = 1) {
            emailService.sendDonationReceipt(
                "donor@example.com", "Jean Dupont", "Asso", "2026-0001", any(),
                "https://app.example.org/login?view=signup&role=donor",
            )
        }
    }

    @Test
    fun `a registered donor gets no invitation`() {
        receiptFor(guest = false)

        service.sendReceiptEmailIfNeeded(donationId)

        verify(exactly = 1) {
            emailService.sendDonationReceipt("donor@example.com", "Jean Dupont", "Asso", "2026-0001", any(), null)
        }
    }
}

package org.commonlink.service

import org.commonlink.entity.DonationReceipt
import org.commonlink.entity.OnchainJobAction
import org.commonlink.event.DonationConfirmedEvent
import org.commonlink.exception.NotFoundException
import org.commonlink.onchain.OnchainCodec
import org.commonlink.repository.DonationReceiptRepository
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Listens for [DonationConfirmedEvent] to asynchronously generate the Cerfa receipt PDF,
 * persist it, hash it (keccak256), and enqueue a RECORD_DONATION job in the on-chain outbox.
 *
 * PDF bytes are persisted in [DonationReceipt] before the chain write. The same bytes are
 * emailed to the donor after the RECORD_DONATION job succeeds — guaranteeing the on-chain
 * hash matches the document the donor receives.
 *
 * Self-injection via [@Lazy] is intentional: it routes [enqueueOnchainJob] and
 * [sendReceiptEmailIfNeeded] calls through the Spring AOP proxy so that [@Transactional] applies.
 */
@Service
@ConditionalOnProperty(prefix = "onchain.worker", name = ["enabled"], havingValue = "true")
class DonationReceiptService(
    private val donationRepository: DonationRepository,
    private val donationReceiptRepository: DonationReceiptRepository,
    private val receiptService: ReceiptService,
    private val receiptNumberService: ReceiptNumberService,
    private val emailService: EmailService,
    private val outbox: OnchainOutboxService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val parisZone = ZoneId.of("Europe/Paris")

    @Autowired @Lazy
    private lateinit var self: DonationReceiptService

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onDonationConfirmed(event: DonationConfirmedEvent) {
        runCatching { self.enqueueOnchainJob(event.donationId) }
            .onFailure { logger.error("Async receipt processing failed for donation {}", event.donationId, it) }
    }

    @Scheduled(
        initialDelayString = "\${donation.receipt.reconciler-initial-delay-ms:30000}",
        fixedDelayString = "\${donation.receipt.reconciler-delay-ms:60000}",
    )
    fun reconcile() {
        val pending = donationRepository.findConfirmedWithoutOnchainJob()
        if (pending.isEmpty()) return
        logger.info("Reconciler: {} confirmed donation(s) without on-chain job", pending.size)
        pending.forEach { donation ->
            runCatching { self.enqueueOnchainJob(donation.id!!) }
                .onFailure { logger.error("Reconciler failed for donation {}", donation.id, it) }
        }
    }

    /**
     * Generates (or reuses) the Cerfa PDF, persists it as a [DonationReceipt], hashes it,
     * and enqueues a RECORD_DONATION outbox job.
     *
     * Idempotent: if a [DonationReceipt] already exists the same bytes are reused, and
     * [OnchainOutboxService.enqueue] deduplicates via `correlationKey`.
     */
    @Transactional
    fun enqueueOnchainJob(donationId: UUID) {
        val donation = donationRepository.findById(donationId)
            .orElseThrow { NotFoundException("Donation not found: $donationId") }

        if (donation.confirmedAt == null) {
            logger.warn("Donation {} is not confirmed, skipping receipt enqueue", donationId)
            return
        }

        val donor = donation.donor
        checkNotNull(donor.walletAddress) {
            "Donor ${donor.id} has no wallet address — should have been derived on confirmDonation"
        }

        // Reuse persisted bytes on idempotent retry; generate once on first call.
        val existing = donationReceiptRepository.findByDonationId(donationId)
        val (receiptBytes, receiptNumber) = if (existing != null) {
            logger.debug("Reusing existing receipt {} for donation {}", existing.receiptNumber, donationId)
            Pair(existing.pdfBytes, existing.receiptNumber)
        } else {
            val year = donation.confirmedAt!!.atZone(parisZone).year
            val number = receiptNumberService.nextNumber(donation.campaign.association.id!!, year)
            val bytes = receiptService.generate(donation, number)
            donationReceiptRepository.save(
                DonationReceipt(
                    donation = donation,
                    receiptNumber = number,
                    pdfBytes = bytes,
                    generatedAt = Instant.now(),
                )
            )
            logger.info("Generated receipt {} for donation {}", number, donationId)
            Pair(bytes, number)
        }

        val receiptHashHex = Numeric.toHexString(Hash.sha3(receiptBytes))

        outbox.enqueue(
            action = OnchainJobAction.RECORD_DONATION,
            payload = RecordDonationPayload(
                donationId     = donation.id!!,
                donor          = donor.walletAddress!!,
                campaignId     = donation.campaign.id!!,
                amountCents    = OnchainCodec.eurToCents(donation.amount),
                receiptHashHex = receiptHashHex,
                txRef          = donation.providerRef,
            ),
            correlationKey = "DONATION:$donationId",
        )
        logger.info("Enqueued RECORD_DONATION for donation {}", donationId)
    }

    /**
     * Sends the fiscal receipt email to the donor, exactly once.
     *
     * Called by [OnchainJobWorker] after RECORD_DONATION hits DONE.
     * The [DonationReceipt.emailedAt] guard prevents duplicate delivery on retries.
     */
    @Transactional
    fun sendReceiptEmailIfNeeded(donationId: UUID) {
        val receipt = donationReceiptRepository.findByDonationId(donationId)
        if (receipt == null) {
            logger.warn("No receipt found for donation {} — cannot send email", donationId)
            return
        }
        if (receipt.emailedAt != null) {
            logger.debug("Receipt email already sent for donation {}, skipping", donationId)
            return
        }

        val donation = receipt.donation
        val donorEmail = donation.donor.user.email
        val donorName = donation.donorFullName ?: donation.donor.user.email
        val associationName = donation.campaign.association.name

        emailService.sendDonationReceipt(
            donorEmail      = donorEmail,
            donorName       = donorName,
            associationName = associationName,
            receiptNumber   = receipt.receiptNumber,
            pdfBytes        = receipt.pdfBytes,
        )

        receipt.emailedAt = Instant.now()
        donationReceiptRepository.save(receipt)
        logger.info("Receipt email sent for donation {} ({})", donationId, receipt.receiptNumber)
    }
}

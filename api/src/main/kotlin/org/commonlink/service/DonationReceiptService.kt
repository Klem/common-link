package org.commonlink.service

import org.commonlink.entity.OnchainJobAction
import org.commonlink.event.DonationConfirmedEvent
import org.commonlink.exception.NotFoundException
import org.commonlink.onchain.OnchainCodec
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.util.UUID

/**
 * Listens for [DonationConfirmedEvent] to asynchronously generate the Cerfa receipt PDF,
 * hash it (keccak256), and enqueue a RECORD_DONATION job in the on-chain outbox.
 *
 * The event listener fires after the confirmation transaction commits ([TransactionPhase.AFTER_COMMIT])
 * and runs on a separate thread ([Async]). A scheduled reconciler recovers any donations whose
 * async processing failed (e.g. crash between commit and event dispatch).
 *
 * Self-injection via [@Lazy] is intentional: it routes [enqueueOnchainJob] calls through the
 * Spring AOP proxy so that [@Transactional] applies (avoids self-invocation bypass).
 */
@Service
class DonationReceiptService(
    private val donationRepository: DonationRepository,
    private val receiptService: ReceiptService,
    private val outbox: OnchainOutboxService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired @Lazy
    private lateinit var self: DonationReceiptService

    /**
     * Async post-commit handler. Runs on the shared task executor, never on the HTTP thread.
     * Failures are logged but do not affect the caller — the reconciler picks them up.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun onDonationConfirmed(event: DonationConfirmedEvent) {
        runCatching { self.enqueueOnchainJob(event.donationId) }
            .onFailure { logger.error("Async receipt processing failed for donation {}", event.donationId, it) }
    }

    /**
     * Periodic reconciler — enqueues RECORD_DONATION for confirmed donations that have no
     * on-chain job yet (missed by the async listener due to crashes or restart).
     *
     * Interval controlled by `donation.receipt.reconciler-delay-ms` (default 60 s).
     */
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
     * Generates the receipt, hashes it, and enqueues a RECORD_DONATION outbox job.
     *
     * Idempotent: [OnchainOutboxService.enqueue] deduplicates via `correlationKey`.
     * Requires an active transaction so lazy associations ([Donation.donor], [Donation.campaign])
     * can be loaded without [org.hibernate.LazyInitializationException].
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

        val receiptBytes = receiptService.generate(donation)
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
}

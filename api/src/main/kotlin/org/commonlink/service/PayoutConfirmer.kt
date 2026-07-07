package org.commonlink.service

import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutStatus
import org.commonlink.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigInteger
import java.time.Instant

/**
 * Transactional confirm step for payout state transitions.
 *
 * Lives in its own Spring-managed bean so the [@Transactional] proxy applies on the
 * external call from [PayoutService] — self-invocation within the same bean bypasses AOP.
 * Setting CONFIRMED and enqueuing the on-chain job happen atomically in one transaction,
 * so a crash never leaves a payout CONFIRMED without a corresponding [OnchainJob].
 */
@Component
class PayoutConfirmer(
    private val payoutRepository: PayoutRepository,
    private val outbox: OnchainOutboxService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Transitions [payout] from PENDING to CONFIRMED and enqueues a [OnchainJobAction.RECORD_PAYOUT] job.
     * The [outbox] call joins the current transaction via default REQUIRED propagation.
     *
     * @throws IllegalStateException if [payout.status] is not PENDING (guard at call site).
     */
    @Transactional
    fun confirmAndEnqueue(payout: Payout): Payout {
        payout.status = PayoutStatus.CONFIRMED
        payout.confirmedAt = Instant.now()
        val saved = payoutRepository.save(payout)

        val job = outbox.enqueue(
            action = OnchainJobAction.RECORD_PAYOUT,
            payload = RecordPayoutPayload(
                payoutId   = saved.id,
                campaignId = saved.campaign.id!!,
                amountCents = BigInteger.valueOf((saved.amount.toLong() * 100)),
            ),
            correlationKey = "PAYOUT:${saved.id}",
        )
        saved.onchainJobId = job.id
        payoutRepository.save(saved)

        log.info("Payout {} confirmed; enqueued RECORD_PAYOUT job {}", saved.id, job.id)
        return saved
    }
}

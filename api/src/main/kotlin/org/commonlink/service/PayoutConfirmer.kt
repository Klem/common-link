package org.commonlink.service

import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.PayeeIban
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutBlockingReason
import org.commonlink.entity.PayoutStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayeeIbanRepository
import org.commonlink.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Everything the Bridge initiation needs, read inside a transaction and usable once it committed.
 *
 * @param payoutId Payout being confirmed.
 * @param campaignId Campaign owning it, used to build the return URL.
 * @param amount Transfer amount.
 * @param label Statement label.
 * @param payerName Name of the association paying, sent as Bridge's mandatory `user` object.
 * @param payerReference Association id, echoed to Bridge as `user.external_reference`.
 * @param payeeName Beneficiary name sent to Bridge.
 * @param payeeIban Beneficiary IBAN — the dynamic beneficiary.
 */
data class PayoutConfirmContext(
    val payoutId: UUID,
    val campaignId: UUID,
    val amount: BigDecimal,
    val label: String,
    val payerName: String,
    val payerReference: String,
    val payeeName: String,
    val payeeIban: String,
)

/**
 * Transactional steps of payout confirmation, split so that no database transaction is ever held
 * open across a Bridge network call.
 *
 * Confirmation runs in three phases, orchestrated by [PayoutService.confirm]:
 *  1. [loadForConfirm] — validate and read what the initiation needs
 *  2. [reserve] — lock the campaign, re-check the balance, mark the amount engaged
 *  3. Bridge call, then [attachPaymentLink] or [releaseReservation]
 *
 * The payout stays PENDING throughout: with Open Banking initiation, nothing moves until the
 * association authorises the transfer at its own bank. Settlement arrives later, through Bridge's
 * webhook, and only then does [finaliseSettled] promote the payout and publish the on-chain
 * attestation.
 *
 * Two orderings here cost real money if reversed:
 *
 * **The on-chain attestation comes last.** Confirmation used to set the payout CONFIRMED and
 * enqueue [OnchainJobAction.RECORD_PAYOUT] before any transfer existed. Had the bank then refused
 * it, the blockchain would permanently assert a payment that never happened. The attestation is now
 * published only on a settled transfer (`ACSC`).
 *
 * **The engagement marker is set under the campaign lock.** While the association is authorising at
 * its bank the payout is still PENDING although its money is committed, so [reserve] stamps
 * [Payout.bridgeStatus] and [confirmableBalance] counts those amounts as spent. Without it, two
 * concurrent confirmations could each pass the balance check and both be authorised.
 *
 * Lives in its own Spring-managed bean so the `@Transactional` proxy applies on calls from
 * [PayoutService] — self-invocation within the same bean bypasses AOP.
 */
@Component
class PayoutConfirmer(
    private val payoutRepository: PayoutRepository,
    private val campaignRepository: CampaignRepository,
    private val payeeIbanRepository: PayeeIbanRepository,
    private val donationRepository: DonationRepository,
    private val outbox: OnchainOutboxService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Validates that [payoutId] may be confirmed and returns what the Bridge initiation needs.
     *
     * @param campaignId Campaign owning the payout.
     * @param payoutId Payout to confirm.
     * @param associationId Association making the request.
     * @return the context needed to create the payment link.
     * @throws NotFoundException if the payout cannot be found for [associationId].
     * @throws ConflictException if the payout is not PENDING, already has an initiation in
     *         progress, or its IBAN is no longer usable.
     */
    @Transactional(readOnly = true)
    fun loadForConfirm(campaignId: UUID, payoutId: UUID, associationId: UUID): PayoutConfirmContext {
        val payout = payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(
            campaignId, payoutId, associationId,
        ) ?: throw NotFoundException("Payout not found: $payoutId")

        if (payout.status != PayoutStatus.PENDING) {
            throw ConflictException("Payout $payoutId is already ${payout.status}")
        }
        if (payout.bridgeStatus != null) {
            throw ConflictException("Payout $payoutId already has a transfer in progress")
        }

        val payeeIban = payeeIbanRepository.findById(payout.payeeIbanId).orElse(null)
        if (payeeIban == null || payeeIban.status != IbanVerificationStatus.VERIFIED || !payeeIban.active) {
            throw ConflictException("Payout blocked: ${PayoutBlockingReason.IBAN_NOT_VERIFIED}")
        }

        return PayoutConfirmContext(
            payoutId = payout.id,
            campaignId = campaignId,
            amount = payout.amount,
            label = payout.label,
            payerName = payout.campaign.association.name,
            payerReference = associationId.toString(),
            payeeName = payout.payee.name,
            payeeIban = payeeIban.iban,
        )
    }

    /**
     * Marks the payout's amount as engaged, under the campaign row lock.
     *
     * Re-validates the balance here rather than trusting [loadForConfirm]: other payouts may have
     * consumed the funds in between, and this is the last point at which nothing has been initiated.
     *
     * @param campaignId Campaign owning the payout.
     * @param payoutId Payout to reserve.
     * @throws NotFoundException if the campaign or payout disappeared.
     * @throws ConflictException if the payout is no longer PENDING, is already engaged, or the
     *   confirmable balance no longer covers it.
     */
    @Transactional
    fun reserve(campaignId: UUID, payoutId: UUID) {
        // Lock the campaign so concurrent confirmations serialize against the balance re-check.
        campaignRepository.findByIdForUpdate(campaignId)
            ?: throw NotFoundException("Campaign not found: $campaignId")

        val payout = payoutRepository.findById(payoutId)
            .orElseThrow { NotFoundException("Payout not found: $payoutId") }

        if (payout.status != PayoutStatus.PENDING) {
            throw ConflictException("Payout $payoutId is already ${payout.status}")
        }
        if (payout.bridgeStatus != null) {
            throw ConflictException("Payout $payoutId already has a transfer in progress")
        }
        if (payout.amount > confirmableBalance(campaignId)) {
            throw ConflictException("Payout blocked: ${PayoutBlockingReason.INSUFFICIENT_BALANCE}")
        }

        payout.bridgeStatus = BridgePaymentStatus.CREA
        payout.bridgeSyncedAt = Instant.now()
        payoutRepository.save(payout)
        log.info("Payout {} reserved for a Bridge transfer initiation", payoutId)
    }

    /**
     * Stores the Bridge payment link so the association can be redirected to its bank.
     *
     * The payout stays PENDING: the transfer only exists once the association authorises it.
     *
     * @param payoutId Payout being initiated.
     * @param link The created Bridge payment link.
     * @return the updated payout.
     * @throws NotFoundException if the payout disappeared.
     */
    @Transactional
    fun attachPaymentLink(payoutId: UUID, link: BridgePaymentLink): Payout {
        val payout = payoutRepository.findById(payoutId)
            .orElseThrow { NotFoundException("Payout not found: $payoutId") }

        payout.bridgePaymentLinkId = link.id
        payout.bridgeCheckoutUrl = link.url
        payout.bridgeStatus = BridgePaymentStatus.CREA
        payout.bridgeLastError = null
        payout.bridgeSyncedAt = Instant.now()

        log.info("Payout {} awaiting bank authorisation via Bridge link {}", payoutId, link.id)
        return payoutRepository.save(payout)
    }

    /**
     * Promotes the payout to CONFIRMED and enqueues the on-chain attestation, the transfer having
     * been settled by the bank.
     *
     * Both writes happen in one transaction, so a crash never leaves a payout CONFIRMED without its
     * [OnchainJobAction.RECORD_PAYOUT] job. Idempotent: a payout already CONFIRMED is returned
     * untouched, because Bridge retries a webhook for up to two days.
     *
     * The row is locked for the whole transaction. That guard is a read-then-write, and Bridge
     * delivers its notifications in parallel: without the lock two threads both read a payout that
     * was not yet CONFIRMED and both enqueued the job, the second one dying on the outbox's unique
     * constraint and answering Bridge 502 for a settlement that had succeeded — see
     * [PayoutRepository.findByIdForUpdate].
     *
     * @param payoutId Payout to settle.
     * @param transactionId Bridge transaction id, for bank reconciliation.
     * @return the confirmed payout.
     * @throws NotFoundException if the payout disappeared.
     */
    @Transactional
    fun finaliseSettled(payoutId: UUID, transactionId: String?): Payout {
        val payout = payoutRepository.findByIdForUpdate(payoutId)
            ?: throw NotFoundException("Payout not found: $payoutId")

        if (payout.status == PayoutStatus.CONFIRMED) {
            log.debug("Payout {} already confirmed — webhook replay ignored", payoutId)
            return payout
        }

        payout.status = PayoutStatus.CONFIRMED
        payout.confirmedAt = Instant.now()
        payout.bridgeStatus = BridgePaymentStatus.ACSC
        payout.bridgePaymentTransactionId = transactionId
        payout.bridgeLastError = null
        payout.bridgeSyncedAt = Instant.now()
        val saved = payoutRepository.save(payout)

        val job = outbox.enqueue(
            action = OnchainJobAction.RECORD_PAYOUT,
            payload = RecordPayoutPayload(
                payoutId = saved.id,
                campaignId = saved.campaign.id!!,
                // movePointRight, not toLong() * 100: truncating first turned 10.50 € into 1000 cents.
                amountCents = saved.amount.movePointRight(2).toBigIntegerExact(),
            ),
            correlationKey = "PAYOUT:${saved.id}",
        )
        saved.onchainJobId = job.id
        payoutRepository.save(saved)

        log.info(
            "Payout {} settled (Bridge transaction {}); enqueued RECORD_PAYOUT job {}",
            saved.id, transactionId, job.id,
        )
        return saved
    }

    /**
     * Deletes a payout whose initiation Bridge never accepted.
     *
     * Nothing was created at Bridge: no link, no authorisation URL, nothing a later notification
     * could refer to. The row would record only that a form failed to submit, and an association
     * that cannot tell it apart from a real attempt simply creates another one — which is how four
     * identical rows appeared on 2026-09-23 from one payment.
     *
     * Deliberately narrow. Only a payout still PENDING and carrying no payment-link id is dropped;
     * anything else is left alone and logged, because a link that exists is a fact this row is the
     * only local record of — including a destination read-back refused, which is evidence of a
     * control `docs/legal/verification-payee-iban.md` describes.
     *
     * @param payoutId Payout to drop.
     */
    @Transactional
    fun discardNeverInitiated(payoutId: UUID) {
        val payout = payoutRepository.findByIdForUpdate(payoutId) ?: return
        if (payout.status != PayoutStatus.PENDING || payout.bridgePaymentLinkId != null) {
            log.warn(
                "Refusing to discard payout {} — it is {} with Bridge link {}",
                payoutId, payout.status, payout.bridgePaymentLinkId,
            )
            return
        }
        payoutRepository.delete(payout)
        log.info("Payout {} discarded — Bridge never accepted the initiation, so nothing was created", payoutId)
    }

    /**
     * Returns a payout to a retryable PENDING state, its amount back on the campaign's balance.
     *
     * For every outcome where **no transfer was ever authorised**: an initiation that never
     * reached Bridge, and a link that died unused — expired or revoked before the association
     * authenticated at its bank. In all of them nothing can have been debited, so the amount must
     * return to the confirmable balance and the association must be able to click confirm again.
     *
     * [finaliseFailed] would be wrong here — it stamps FAILED, which [loadForConfirm] refuses, so
     * closing the tab on the bank's page would retire a payout for good over an outcome that moved
     * no money. That distinction is the whole point: a rejection is the bank refusing, an expiry is
     * nobody ever asking. Only the first is terminal.
     *
     * The diagnostic is kept on the row so the association is told why, and
     * [Payout.bridgeCheckoutUrl] is cleared: it points at a link that can no longer be used.
     * [Payout.bridgePaymentLinkId] stays, as the audit trail of the attempt and as the routing key
     * for any notification Bridge still has in flight for it.
     *
     * @param payoutId Payout whose reservation is released.
     * @param message Why it is being released, stored on the row for support.
     */
    @Transactional
    fun releaseReservation(payoutId: UUID, message: String) {
        val payout = payoutRepository.findByIdForUpdate(payoutId) ?: return
        if (payout.status != PayoutStatus.PENDING) {
            log.warn("Refusing to release payout {} — it is {}", payoutId, payout.status)
            return
        }
        payout.bridgeStatus = null
        payout.bridgeCheckoutUrl = null
        payout.bridgeLastError = message.take(BRIDGE_ERROR_MAX_LENGTH)
        payout.bridgeSyncedAt = Instant.now()
        payoutRepository.save(payout)
        log.warn("Payout {} released back to a retryable PENDING: {}", payoutId, message)
    }

    /**
     * Marks the payout FAILED — the bank refused the transfer.
     *
     * Reserved for a refusal, which is terminal: the bank was asked and said no. A link that
     * merely died unused goes through [releaseReservation] instead, because nobody ever asked.
     *
     * The reservation is released and the amount returns to the campaign's confirmable balance.
     * This is safe in the initiation model: no money can move without the association authorising
     * it at its bank, so a refused initiation moved nothing. No on-chain attestation is emitted —
     * nothing certifies a transfer that did not happen.
     *
     * [Payout.bridgeCheckoutUrl] is cleared: the caller revokes the link before failing the payout,
     * precisely so it cannot be authorised after the amount has gone back to the balance, and an
     * URL kept on the row would only point at that dead link.
     *
     * @param payoutId Payout to fail.
     * @param message Diagnostic message stored on the row.
     * @param bridgeStatus Bridge's own terminal state, when known.
     */
    @Transactional
    fun finaliseFailed(payoutId: UUID, message: String, bridgeStatus: BridgePaymentStatus?) {
        val payout = payoutRepository.findByIdForUpdate(payoutId) ?: return
        if (payout.status == PayoutStatus.CONFIRMED) {
            log.warn("Refusing to fail payout {} — it is already confirmed as settled", payoutId)
            return
        }
        payout.status = PayoutStatus.FAILED
        payout.bridgeStatus = bridgeStatus
        payout.bridgeCheckoutUrl = null
        payout.bridgeLastError = message.take(BRIDGE_ERROR_MAX_LENGTH)
        payout.bridgeSyncedAt = Instant.now()
        payoutRepository.save(payout)
        log.warn("Payout {} failed at Bridge: {}", payoutId, message)
    }

    /**
     * Records an intermediate Bridge state (`ACTC`, `PDNG`, `PART`) without changing [PayoutStatus].
     *
     * The transfer is authorised but not settled: the amount must stay engaged, and the payout must
     * not yet claim the beneficiary has been credited.
     *
     * Refuses to touch a payout already CONFIRMED, same guard and same reason as [finaliseFailed].
     * Bridge fires `payment.transaction.updated` and `payment.link.updated` concurrently for a
     * single state change — both were observed 98 ms apart on the same row — so at settlement two
     * threads read Bridge independently: one can see `ACSC` while the other still sees `PDNG`. With
     * the in-flight write landing second, the payout would stay CONFIRMED (the on-chain attestation
     * is safe, [finaliseSettled] being idempotent) while permanently displaying `PDNG`.
     *
     * @param payoutId Payout to update.
     * @param bridgeStatus The intermediate Bridge state.
     * @param transactionId Bridge transaction id, when already known.
     */
    @Transactional
    fun recordInFlight(payoutId: UUID, bridgeStatus: BridgePaymentStatus, transactionId: String?) {
        val payout = payoutRepository.findByIdForUpdate(payoutId) ?: return
        if (payout.status == PayoutStatus.CONFIRMED) {
            log.warn("Refusing to record {} on payout {} — it is already confirmed as settled", bridgeStatus, payoutId)
            return
        }
        payout.bridgeStatus = bridgeStatus
        payout.bridgePaymentTransactionId = transactionId ?: payout.bridgePaymentTransactionId
        payout.bridgeSyncedAt = Instant.now()
        payoutRepository.save(payout)
        log.info("Payout {} now {} at Bridge", payoutId, bridgeStatus)
    }

    /**
     * Funds confirmable right now = confirmed donations − CONFIRMED payouts − engaged payouts.
     *
     * Mirrors [PayoutService]'s own computation; duplicated here rather than shared because this
     * one runs under the campaign lock and additionally deducts initiations in progress.
     */
    private fun confirmableBalance(campaignId: UUID): BigDecimal {
        val confirmed = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED)
            ?: BigDecimal.ZERO
        val inFlight = payoutRepository.sumInFlightAmountByCampaignId(campaignId) ?: BigDecimal.ZERO
        val raised = donationRepository.sumConfirmedAmountByCampaignId(campaignId) ?: BigDecimal.ZERO
        return raised - confirmed - inFlight
    }

    private companion object {
        /** Matches the `bridge_last_error` column width. */
        const val BRIDGE_ERROR_MAX_LENGTH = 500
    }
}

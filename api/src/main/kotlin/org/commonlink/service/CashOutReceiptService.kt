package org.commonlink.service

import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.CashOutNotCashedOutException
import org.commonlink.exception.HelloAssoException
import org.commonlink.exception.HelloAssoNotLinkedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * One donation of a HelloAsso payout, as returned by the transfer receipt confirmation.
 *
 * @property availableAt When it became available ([org.commonlink.entity.Donation.fundsAvailableAt]).
 * @property confirmationMode How: by this confirmation, by the settlement delay, or by a curator.
 */
data class CashOutReceiptDonation(
    val donationId: UUID,
    val campaignId: UUID,
    val amount: BigDecimal,
    val availableAt: Instant?,
    val confirmationMode: ReceiptConfirmationMode?,
)

/**
 * Outcome of [CashOutReceiptService.confirm].
 *
 * @property donations The association's donations of the payout that are available (all of them once confirmed).
 * @property netTotal Net amount of the payout at HelloAsso, in euros; null when not readable (it never blocks).
 */
data class CashOutReceiptResult(val idCashOut: Long, val donations: List<CashOutReceiptDonation>, val netTotal: BigDecimal?)

/**
 * "Transfer received": the association states that HelloAsso payout [idCashOut] reached its bank account, which makes
 * its CommonLink donations available before the settlement delay ends (CEO decision Q3: the association alone
 * confirms). Covers the whole payout, every campaign of the association.
 *
 * Never trusts the click alone: each donation is **re-read at HelloAsso** first (`GET /v5/payments/{id}`) and must be
 * `CashedOut` in this very payout. Sequence:
 * 1. the association's rows of the payout; none → 404. WITHDRAWN rows (refunded, contested) are left out and stay
 *    withdrawn. Rows already AVAILABLE (settlement delay, curator) are not re-read; all of them → idempotent no-op.
 * 2. re-read of the others, out of any transaction, with one token. A refund discovered there goes through
 *    [HelloAssoDonationFundsService.recordReversal] (one alert, one journal entry) and leaves the payout. One
 *    donation not `CashedOut` in this payout → `409 CASH_OUT_NOT_CASHED_OUT`, nothing made available.
 * 3. one transaction: rows locked in donation id order, AVAILABLE, `fundsAvailableAt = now` (never back-dated),
 *    receipt confirmed by the user ([ReceiptConfirmationMode.MANUAL]); one compliance journal entry
 *    ([ComplianceAuditLogService.CASHOUT_RECEIPT_CONFIRMED]) with the donations actually made available.
 * 4. after the transaction, the payout's net total ([HelloAssoCashOutTotalService]), never blocking.
 *
 * Without an ACTIVE HelloAsso link (disconnected, broken, token renewal rejected) → `409 HELLOASSO_NOT_LINKED`: the
 * funds then go through a curator release ([HelloAssoFundsReleaseService]). HelloAsso unavailable, rate limiting or
 * rejecting one payment read → 502.
 * No transition in either case.
 */
@Service
class CashOutReceiptService(
    private val fundsRepository: HelloAssoDonationFundsRepository,
    private val connectionRepository: HelloAssoConnectionRepository,
    private val tokenManager: HelloAssoTokenManager,
    private val breaker: HelloAssoConnectionBreaker,
    private val gateway: HelloAssoGateway,
    private val donationFundsService: HelloAssoDonationFundsService,
    private val cashOutTotalService: HelloAssoCashOutTotalService,
    private val complianceAuditLogService: ComplianceAuditLogService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Time source; replaced by tests. */
    internal var clock: Clock = Clock.systemUTC()

    private companion object {
        const val CASHED_OUT = "CashedOut"
    }

    /**
     * Confirms that payout [idCashOut] of [associationId] was received, on behalf of user [actorUserId]. See the class
     * documentation for the sequence and refusals. Must not run inside a transaction (HelloAsso calls).
     *
     * @throws NotFoundException when no donation of the association is in this payout (also for another
     *   association's payout: no existence leak).
     * @throws CashOutNotCashedOutException when a donation is not paid out in this payout at HelloAsso.
     * @throws HelloAssoNotLinkedException without an active HelloAsso link.
     * @throws BadGatewayException when HelloAsso cannot be read.
     */
    fun confirm(associationId: UUID, idCashOut: Long, actorUserId: UUID): CashOutReceiptResult {
        val rows = fundsRepository.findByAssociationIdAndIdCashOut(associationId, idCashOut)
        if (rows.isEmpty()) throw NotFoundException("No donation of this association in HelloAsso payout $idCashOut")
        val pending = rows.filter { it.fundsState != FundsState.AVAILABLE && it.fundsState != FundsState.WITHDRAWN }
        if (pending.isEmpty()) {
            log.info("HelloAsso payout {} of association {} already confirmed: no-op", idCashOut, associationId)
            return result(associationId, idCashOut, null)
        }

        val connection = connectionRepository.findByAssociationId(associationId)
        if (connection?.state != HelloAssoConnectionState.ACTIVE) throw HelloAssoNotLinkedException()
        val token = helloAsso(associationId, breakOnUnauthorized = true) { tokenManager.getValidAccessToken(associationId) }

        val confirmable = mutableListOf<UUID>()
        var notPaidOut = false
        for (row in pending) {
            val payment = helloAsso(associationId, breakOnUnauthorized = false) { gateway.getPayment(token, row.haPaymentId) }
            val outcome = HelloAssoFundsStateMapper.map(
                row.fundsState,
                HelloAssoFundsStateMapper.Observation(
                    payment.state, payment.cashOutState, donationFundsService.parse(payment.cashOutDate), payment.idCashOut, clock.instant(),
                ),
            )
            when {
                outcome.target == FundsState.WITHDRAWN ->
                    transactionTemplate.executeWithoutResult {
                        // Lock and refresh first: the row is already managed (read above, open-in-view), and
                        // recordReversal must see its current state to journal the reversal once.
                        fundsRepository.lockAllForUpdate(listOf(row.donationId!!))
                        donationFundsService.recordReversal(row.donation, payment.state, payment.cashOutState)
                    }
                payment.cashOutState == CASHED_OUT && payment.idCashOut == idCashOut -> confirmable += row.donationId!!
                else -> {
                    log.info("HelloAsso payment {} is {} (payout {}), not paid out in payout {}", row.haPaymentId, payment.cashOutState, payment.idCashOut, idCashOut)
                    notPaidOut = true
                }
            }
        }
        if (notPaidOut) throw CashOutNotCashedOutException(idCashOut)

        transactionTemplate.executeWithoutResult { makeAvailable(associationId, idCashOut, confirmable, actorUserId) }
        val netTotal = cashOutTotalService.netTotalOf(associationId, idCashOut, token, connection.organizationSlug)
        return result(associationId, idCashOut, netTotal)
    }

    /** Step 3, in the caller's transaction: rows still pending become AVAILABLE; journaled once if any did. */
    private fun makeAvailable(associationId: UUID, idCashOut: Long, donationIds: List<UUID>, actorUserId: UUID) {
        val now = clock.instant()
        val made = fundsRepository.lockAllForUpdate(donationIds)
            .filter { it.fundsState != FundsState.AVAILABLE && it.fundsState != FundsState.WITHDRAWN }
        for (row in made) {
            row.fundsState = FundsState.AVAILABLE
            row.cashOutState = CASHED_OUT
            row.receiptConfirmedAt = now
            row.receiptConfirmationMode = ReceiptConfirmationMode.MANUAL
            row.receiptConfirmedBy = actorUserId
            row.lastCheckedAt = now
            row.updatedAt = now
            row.donation.fundsAvailableAt = now
            fundsRepository.save(row)
        }
        if (made.isEmpty()) return
        val amount = made.fold(BigDecimal.ZERO) { sum, row -> sum + row.donation.amount }
        complianceAuditLogService.append(
            eventType = ComplianceAuditLogService.CASHOUT_RECEIPT_CONFIRMED,
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            payload = mapOf("idCashOut" to idCashOut, "donationCount" to made.size, "amount" to amount.toPlainString()),
            subjectId = associationId,
            actorUserId = actorUserId,
        )
        log.info("HelloAsso payout {} of association {} confirmed received: {} donation(s) available", idCashOut, associationId, made.size)
    }

    private fun result(associationId: UUID, idCashOut: Long, netTotal: BigDecimal?): CashOutReceiptResult {
        val available = fundsRepository.findByAssociationIdAndIdCashOut(associationId, idCashOut)
            .filter { it.fundsState == FundsState.AVAILABLE }
            .map { it.toReceiptDonation() }
        return CashOutReceiptResult(idCashOut, available, netTotal)
    }

    private fun HelloAssoDonationFunds.toReceiptDonation() = CashOutReceiptDonation(
        donationId = donationId!!,
        campaignId = donation.campaign.id!!,
        amount = donation.amount,
        availableAt = donation.fundsAvailableAt,
        confirmationMode = receiptConfirmationMode,
    )

    /**
     * Runs a HelloAsso call, mapping its failures to the API's answers (see the class documentation). As in the
     * tracking pass ([HelloAssoCashOutSyncExecutor]), only a rejected token renewal breaks the link
     * ([breakOnUnauthorized]); a 401 on one payment read is answered 502 and leaves the link as it is.
     */
    private fun <T> helloAsso(associationId: UUID, breakOnUnauthorized: Boolean, call: () -> T): T = try {
        call()
    } catch (ex: HelloAssoUnauthorizedException) {
        if (!breakOnUnauthorized) {
            log.warn("HelloAsso rejected a payment read while confirming a payout of association {}", associationId)
            throw BadGatewayException("HelloAsso is unavailable, try again later")
        }
        breaker.markBroken(associationId)
        throw HelloAssoNotLinkedException()
    } catch (ex: IllegalStateException) {
        throw HelloAssoNotLinkedException()
    } catch (ex: HelloAssoException) {
        log.warn("HelloAsso unreadable while confirming a payout of association {}: {}", associationId, ex.javaClass.simpleName)
        throw BadGatewayException("HelloAsso is unavailable, try again later")
    }
}

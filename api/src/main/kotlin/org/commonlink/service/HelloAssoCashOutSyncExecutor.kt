package org.commonlink.service

import org.commonlink.config.PaymentsProperties
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.HelloAssoException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.repository.HelloAssoCashOutSyncRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.commonlink.service.HelloAssoFundsStateMapper.Signal
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Outcome of the last cash-out tracking pass of an association, as shown in the Payments tab.
 *
 * @property lastSyncAt End of the last pass that read (or tried to read) HelloAsso; null before the first one.
 * @property syncError Why it failed, as a stable code ([HelloAssoCashOutSyncExecutor.ERROR_RATE_LIMITED]…); null
 *   when it succeeded.
 */
data class CashOutSyncResult(val lastSyncAt: Instant?, val syncError: String?)

/**
 * Cash-out tracking of HelloAsso donations: reads, payment by payment, where HelloAsso stands in paying each donation
 * out (`GET /v5/payments/{id}`, no HelloAsso notification exists for payouts, `meta.updatedAt` is not relied on), then
 * makes paid-out donations available once the settlement delay has elapsed.
 *
 * One pass of an association ([syncAssociation]):
 * 1. **Polling** — only with an ACTIVE HelloAsso link, one access token per association and per pass. Each row still
 *    to track is read and mapped by [HelloAssoFundsStateMapper], written under its row lock. A refund or contestation
 *    goes through [HelloAssoDonationFundsService.recordReversal], the same path as a refund notification (one alert,
 *    one journal entry). An HTTP error makes no transition; a 429 stops the pass for this association; any other
 *    error moves on to the next row. Known limit: only rows not yet AVAILABLE nor WITHDRAWN are read, so a refund
 *    after AVAILABLE is seen only through HelloAsso's notification. A real "refunded" donation status and the
 *    cancellation of its tax receipt are out of scope.
 * 2. **Settlement** — every IN_TRANSIT row, link active or not (no HelloAsso call): AVAILABLE once
 *    [org.commonlink.config.HelloAssoCashOutProperties.cashoutSettlementBusinessDays] TARGET2 business days have
 *    passed since HelloAsso's payout date (or, without it, since `CashedOut` was first seen). The donation's
 *    `fundsAvailableAt` is set to **now**, never back-dated, even when the pass runs late: the FIFO stays append-only.
 * 3. **Alerts** — once per event, deduplicated on columns: payment stuck (`CantTransferReceiverFull`), unknown state
 *    persisting, paid out but not arrived for too long, repeated pass failures, donations not paid out while the link
 *    is disconnected or broken (manual release needed, CEO decision Q5).
 * 4. **Result** — [CashOutSyncResult] stored per association (`helloasso_cash_out_syncs`).
 *
 * Guard: when no row is to track, [syncAll] returns without any call (no token, no HTTP). Never throws.
 */
@Service
class HelloAssoCashOutSyncExecutor(
    private val fundsRepository: HelloAssoDonationFundsRepository,
    private val syncRepository: HelloAssoCashOutSyncRepository,
    private val connectionRepository: HelloAssoConnectionRepository,
    private val tokenManager: HelloAssoTokenManager,
    private val breaker: HelloAssoConnectionBreaker,
    private val gateway: HelloAssoGateway,
    private val donationFundsService: HelloAssoDonationFundsService,
    private val technicalAlertService: TechnicalAlertService,
    private val properties: PaymentsProperties,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Time source; replaced by tests to move across business days. */
    internal var clock: Clock = Clock.systemUTC()

    companion object {
        const val ERROR_RATE_LIMITED = "RATE_LIMITED"
        const val ERROR_UNAUTHORIZED = "UNAUTHORIZED"
        const val ERROR_UNAVAILABLE = "UNAVAILABLE"
        const val ERROR_NOT_LINKED = "NOT_LINKED"
        private val UNLINKED_STATES = setOf(HelloAssoConnectionState.DISCONNECTED, HelloAssoConnectionState.BROKEN)
        private val NOT_PAID_OUT = setOf(FundsState.AT_PROVIDER, FundsState.PAYOUT_PREPARING)
    }

    private val settings get() = properties.helloasso

    /** One pass over every association with donations to track. Returns at once, with no call, when there is none. */
    fun syncAll() {
        if (!fundsRepository.existsTrackable()) {
            log.debug("HelloAsso cash-out tracking: nothing to track")
            return
        }
        val byAssociation = fundsRepository.findTrackable().groupBy { it.donation.campaign.association.id!! }
        for ((associationId, rows) in byAssociation) {
            try {
                pass(associationId, rows)
            } catch (ex: Exception) {
                log.error("HelloAsso cash-out tracking failed for association {}: {}", associationId, ex.javaClass.simpleName)
            }
        }
        log.info("HelloAsso cash-out tracking: {} association(s), {} donation(s)", byAssociation.size, byAssociation.values.sumOf { it.size })
    }

    /** One pass for [associationId]; returns its stored result. Never throws. */
    fun syncAssociation(associationId: UUID): CashOutSyncResult {
        val rows = fundsRepository.findTrackableByAssociationId(associationId)
        if (rows.isNotEmpty()) {
            try {
                pass(associationId, rows)
            } catch (ex: Exception) {
                log.error("HelloAsso cash-out tracking failed for association {}: {}", associationId, ex.javaClass.simpleName)
            }
        }
        return resultOf(associationId)
    }

    /** The stored result of [associationId]'s last pass. */
    fun resultOf(associationId: UUID): CashOutSyncResult =
        syncRepository.findById(associationId).map { CashOutSyncResult(it.lastSyncAt, it.syncError) }
            .orElse(CashOutSyncResult(null, null))

    private fun pass(associationId: UUID, rows: List<HelloAssoDonationFunds>) {
        transactionTemplate.executeWithoutResult {
            syncRepository.insertIfMissing(associationId)
            // Every pass counts in the on-demand refresh interval (HelloAssoCashOutRefreshService).
            val now = clock.instant()
            syncRepository.claimRefresh(associationId, now, now)
        }
        val connection = connectionRepository.findByAssociationId(associationId)
        val error: String? = if (connection?.state == HelloAssoConnectionState.ACTIVE) {
            clearUnlinkedAlert(associationId)
            poll(associationId, rows)
        } else {
            reportUnlinked(associationId, connection?.state, rows)
            ERROR_NOT_LINKED
        }
        settle(rows.map { it.donationId!! })
        recordResult(associationId, error)
    }

    /** Reads every row at HelloAsso. Returns the pass error, if any. */
    private fun poll(associationId: UUID, rows: List<HelloAssoDonationFunds>): String? {
        val token = try {
            tokenManager.getValidAccessToken(associationId)
        } catch (ex: HelloAssoUnauthorizedException) {
            breaker.markBroken(associationId)
            return ERROR_UNAUTHORIZED
        } catch (ex: HelloAssoException) {
            log.warn("HelloAsso cash-out tracking: no token for association {}: {}", associationId, ex.javaClass.simpleName)
            return ERROR_UNAVAILABLE
        } catch (ex: IllegalStateException) {
            return ERROR_NOT_LINKED
        }
        var error: String? = null
        for (row in rows) {
            try {
                val payment = gateway.getPayment(token, row.haPaymentId)
                transactionTemplate.executeWithoutResult {
                    apply(row.donationId!!, payment.state, payment.cashOutState, donationFundsService.parse(payment.cashOutDate), payment.idCashOut)
                }
            } catch (ex: HelloAssoRateLimitedException) {
                log.warn("HelloAsso cash-out tracking: rate limited for association {}, pass stopped", associationId)
                return ERROR_RATE_LIMITED
            } catch (ex: HelloAssoUnauthorizedException) {
                log.warn("HelloAsso cash-out tracking: token rejected for association {}, pass stopped", associationId)
                return ERROR_UNAUTHORIZED
            } catch (ex: HelloAssoException) {
                log.warn("HelloAsso cash-out tracking: payment {} unreadable: {}", row.haPaymentId, ex.javaClass.simpleName)
                error = ERROR_UNAVAILABLE
            }
        }
        return error
    }

    /** Applies one HelloAsso observation to a row, under its lock. Runs in a transaction. */
    private fun apply(donationId: UUID, state: String?, cashOutState: String?, cashOutDate: Instant?, idCashOut: Long?) {
        val row = fundsRepository.findByDonationIdForUpdate(donationId) ?: return
        val now = clock.instant()
        val outcome = HelloAssoFundsStateMapper.map(
            row.fundsState, HelloAssoFundsStateMapper.Observation(state, cashOutState, cashOutDate, idCashOut, now),
        )
        if (outcome.target == FundsState.WITHDRAWN) {
            donationFundsService.recordReversal(row.donation, state, cashOutState)
            return
        }
        if (row.fundsState == FundsState.AVAILABLE || row.fundsState == FundsState.WITHDRAWN) return

        val previousCashOutState = row.cashOutState
        outcome.target?.let { row.fundsState = it }
        outcome.cashOutDate?.let { row.cashOutDate = it }
        outcome.idCashOut?.let { row.idCashOut = it }
        if (row.cashedOutSeenAt == null) outcome.cashedOutSeenAt?.let { row.cashedOutSeenAt = it }
        row.cashOutState = cashOutState
        row.lastCheckedAt = now
        row.updatedAt = now

        if (Signal.UNKNOWN_STATE in outcome.signals || Signal.UNRECOGNISED_STATE in outcome.signals) {
            if (Signal.UNRECOGNISED_STATE in outcome.signals) {
                log.warn("HelloAsso payment {} has an unexpected payout state {}", row.haPaymentId, cashOutState)
            } else {
                log.warn("HelloAsso payment {} has an unknown payout state", row.haPaymentId)
            }
            val since = row.unknownSince ?: now.also { row.unknownSince = it }
            if (row.unknownAlertedAt == null && Duration.between(since, now) >= settings.cashoutUnknownAlertAfter) {
                row.unknownAlertedAt = now
                technicalAlertService.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_STATE_UNKNOWN, row.donation.providerRef, cashOutState)
            }
        } else {
            row.unknownSince = null
            row.unknownAlertedAt = null
        }
        if (Signal.REGRESSION_IGNORED in outcome.signals) {
            log.warn("HelloAsso payment {} reported {} while {}: ignored", row.haPaymentId, cashOutState, row.fundsState)
        }
        if (Signal.RECEIVER_FULL in outcome.signals && previousCashOutState != cashOutState) {
            technicalAlertService.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_RECEIVER_FULL, row.donation.providerRef)
        }
        fundsRepository.save(row)
    }

    /** IN_TRANSIT → AVAILABLE once the settlement delay has elapsed; alerts a delay far exceeded. */
    private fun settle(donationIds: List<UUID>) {
        for (donationId in donationIds) {
            transactionTemplate.executeWithoutResult {
                val row = fundsRepository.findByDonationIdForUpdate(donationId) ?: return@executeWithoutResult
                if (row.fundsState != FundsState.IN_TRANSIT) return@executeWithoutResult
                val start = row.cashOutDate ?: row.cashedOutSeenAt ?: return@executeWithoutResult
                val now = clock.instant()
                val today = BusinessDayCalendar.dayOf(now)
                val startDay = BusinessDayCalendar.dayOf(start)
                if (!today.isBefore(BusinessDayCalendar.addBusinessDays(startDay, settings.cashoutSettlementBusinessDays))) {
                    row.fundsState = FundsState.AVAILABLE
                    row.receiptConfirmedAt = now
                    row.receiptConfirmationMode = ReceiptConfirmationMode.AUTO
                    row.updatedAt = now
                    row.donation.fundsAvailableAt = now
                    fundsRepository.save(row)
                    log.info("HelloAsso donation {} available (settlement delay elapsed)", donationId)
                } else if (row.inTransitAlertedAt == null &&
                    !today.isBefore(BusinessDayCalendar.addBusinessDays(startDay, settings.cashoutInTransitAlertBusinessDays))
                ) {
                    row.inTransitAlertedAt = now
                    fundsRepository.save(row)
                    technicalAlertService.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_IN_TRANSIT_TOO_LONG, row.donation.providerRef)
                }
            }
        }
    }

    /** Alerts once that donations not paid out yet can no longer be tracked (link disconnected or broken). */
    private fun reportUnlinked(associationId: UUID, state: HelloAssoConnectionState?, rows: List<HelloAssoDonationFunds>) {
        if (state !in UNLINKED_STATES) return
        val notPaidOut = rows.filter { it.fundsState in NOT_PAID_OUT }
        if (notPaidOut.isEmpty()) return
        transactionTemplate.executeWithoutResult {
            val sync = syncRepository.findById(associationId).orElse(null) ?: return@executeWithoutResult
            if (sync.unlinkedAlertedAt != null) return@executeWithoutResult
            sync.unlinkedAlertedAt = clock.instant()
            syncRepository.save(sync)
            val amount = notPaidOut.fold(BigDecimal.ZERO) { sum, row -> sum + row.donation.amount }
            log.warn("HelloAsso link of association {} is {} with {} donation(s) not paid out ({} EUR)", associationId, state, notPaidOut.size, amount)
            technicalAlertService.reportForSubject(
                TechnicalAlertKind.HELLOASSO_CASHOUT_UNLINKED, "association $associationId", "${notPaidOut.size} don(s), $amount EUR",
            )
        }
    }

    private fun clearUnlinkedAlert(associationId: UUID) {
        transactionTemplate.executeWithoutResult {
            val sync = syncRepository.findById(associationId).orElse(null) ?: return@executeWithoutResult
            if (sync.unlinkedAlertedAt == null) return@executeWithoutResult
            sync.unlinkedAlertedAt = null
            syncRepository.save(sync)
        }
    }

    /** Stores the pass outcome and alerts once per failure streak beyond the threshold. Unlinked is not a failure. */
    private fun recordResult(associationId: UUID, error: String?) {
        transactionTemplate.executeWithoutResult {
            val sync = syncRepository.findById(associationId).orElse(null) ?: return@executeWithoutResult
            val now = clock.instant()
            sync.lastSyncAt = now
            sync.syncError = error
            sync.updatedAt = now
            if (error == null || error == ERROR_NOT_LINKED) {
                sync.consecutiveFailures = 0
                sync.failureAlertedAt = null
            } else {
                sync.consecutiveFailures++
                if (sync.failureAlertedAt == null && sync.consecutiveFailures >= settings.cashoutSyncFailureAlertAfter) {
                    sync.failureAlertedAt = now
                    technicalAlertService.reportForSubject(
                        TechnicalAlertKind.HELLOASSO_CASHOUT_SYNC_FAILING, "association $associationId", error,
                    )
                }
            }
            syncRepository.save(sync)
        }
    }
}

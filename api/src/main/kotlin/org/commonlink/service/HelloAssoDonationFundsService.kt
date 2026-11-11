package org.commonlink.service

import org.commonlink.dto.HelloAssoPaymentJson
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.Donation
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Writes the cash-out tracking of HelloAsso donations ([HelloAssoDonationFunds]) at the two points where the checkout
 * flow learns something about a payment: its confirmation ([track]) and its reversal ([recordReversal], also used by
 * the tracking pass). Every transition goes through [HelloAssoFundsStateMapper]. Callers own the transaction.
 */
@Service
class HelloAssoDonationFundsService(
    private val repository: HelloAssoDonationFundsRepository,
    private val technicalAlertService: TechnicalAlertService,
    private val complianceAuditLogService: ComplianceAuditLogService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Creates the tracking row of [donation], just confirmed by HelloAsso payment [payment] (the one retained as paid),
     * in the state its payout fields give. Idempotent: an existing row is left unchanged. An `Unknown` or absent
     * payout state starts [FundsState.AT_PROVIDER] (where the money is at confirmation) and is watched as unknown.
     * Donations confirmed before the tracking existed get no row (user decision T2 = (a)).
     *
     * Never blocks the confirmation it belongs to: a paying payment without id (not expected from HelloAsso) leaves
     * the donation confirmed but untracked — it never becomes available on its own — and raises
     * [TechnicalAlertKind.HELLOASSO_CASHOUT_UNTRACKED] for a technical intervention (the curator release only covers
     * tracked donations).
     */
    fun track(donation: Donation, payment: HelloAssoPaymentJson) {
        if (repository.existsById(donation.id!!)) return
        val paymentId = payment.id
        if (paymentId == null) {
            log.error("HelloAsso donation {} confirmed by a payment without id: cash-out not tracked", donation.id)
            technicalAlertService.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNTRACKED, donation.providerRef)
            return
        }
        val now = Instant.now()
        val outcome = HelloAssoFundsStateMapper.map(
            null,
            HelloAssoFundsStateMapper.Observation(payment.state, payment.cashOutState, parse(payment.cashOutDate), payment.idCashOut, now),
        )
        val row = HelloAssoDonationFunds(
            donation = donation,
            haPaymentId = paymentId,
            cashOutState = payment.cashOutState,
            fundsState = outcome.target ?: FundsState.AT_PROVIDER,
            cashOutDate = outcome.cashOutDate,
            idCashOut = outcome.idCashOut,
            cashedOutSeenAt = outcome.cashedOutSeenAt,
            lastCheckedAt = now,
            unknownSince = now.takeIf { outcome.target == null },
        )
        repository.save(row)
        log.info("HelloAsso donation {} tracked on payment {}: {} ({})", donation.id, paymentId, row.fundsState, payment.cashOutState)
        if (HelloAssoFundsStateMapper.Signal.RECEIVER_FULL in outcome.signals) {
            technicalAlertService.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_RECEIVER_FULL, donation.providerRef)
        }
    }

    /**
     * Records that HelloAsso reports [donation]'s payment as reversed ([state] or [cashOutState] refunded or
     * contested): its tracking row, if any, becomes [FundsState.WITHDRAWN], and the reversal is alerted
     * ([TechnicalAlertKind.PAYMENT_REVERSED]) and journaled ([ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED])
     * with the funds state it had — the journal is what traces a withdrawal of money already
     * [FundsState.AVAILABLE]. The journal entry is written once per donation: a row already WITHDRAWN is not
     * journaled again. The alert is raised on every report, as before the tracking existed, so that a report
     * swallowed by the alert cooldown is not lost for good. A donation without row (confirmed before the tracking
     * existed) is alerted and journaled on every report, as before. The tracking pass never reports a WITHDRAWN row
     * again (it no longer reads it).
     *
     * Must run in a transaction: the row is read under its lock.
     */
    fun recordReversal(donation: Donation, state: String?, cashOutState: String?) {
        val row = repository.findByDonationIdForUpdate(donation.id!!)
        val previous = row?.fundsState
        if (previous == FundsState.WITHDRAWN) {
            log.info("HelloAsso donation {} already withdrawn; reversal {} / {} reported again", donation.id, state, cashOutState)
            technicalAlertService.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, donation.providerRef, null)
            return
        }
        if (row != null) {
            row.fundsState = FundsState.WITHDRAWN
            row.cashOutState = cashOutState ?: row.cashOutState
            row.lastCheckedAt = Instant.now()
            row.updatedAt = Instant.now()
            repository.save(row)
        }
        log.warn("HelloAsso reports donation {} as {} / {} (funds were {})", donation.id, state, cashOutState, previous)
        technicalAlertService.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, donation.providerRef, null)
        complianceAuditLogService.append(
            eventType = ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED,
            subjectType = ComplianceAuditSubjectType.DONATION,
            payload = buildMap {
                put("providerRef", donation.providerRef)
                put("state", state ?: cashOutState)
                if (row != null) put("previousFundsState", previous!!.name)
            },
            subjectId = donation.id,
        )
    }

    /** HelloAsso dates are ISO-8601 with offset; an unreadable one counts as absent. */
    fun parse(date: String?): Instant? = date?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
}

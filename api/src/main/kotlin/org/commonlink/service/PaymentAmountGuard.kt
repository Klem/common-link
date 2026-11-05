package org.commonlink.service

import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.Donation
import org.commonlink.repository.ComplianceAuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Last check before a paid payment confirms its donation, for every provider: the amount re-read at the provider must
 * be exactly the donation's. A confirmation issues a fiscal receipt and an on-chain proof of [Donation.amount], so
 * both must be backed by what the provider actually collected.
 *
 * On a mismatch the donation stays pending: ERROR log, technical alert (throttled by its cooldown) and one compliance
 * journal entry per donation — the re-reads that follow (resent notifications, reconciler) do not add more.
 */
@Service
class PaymentAmountGuard(
    private val technicalAlertService: TechnicalAlertService,
    private val complianceAuditLogService: ComplianceAuditLogService,
    private val complianceAuditLogRepository: ComplianceAuditLogRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Whether [paid] (EUR, as re-read at the provider) equals [donation]'s amount, scale ignored. Reports the mismatch
     * otherwise, a missing amount included.
     */
    fun matches(donation: Donation, paid: BigDecimal?): Boolean {
        if (paid != null && paid.compareTo(donation.amount) == 0) return true
        log.error("Payment of donation {} carries {} EUR instead of {}: not confirmed", donation.id, paid, donation.amount)
        technicalAlertService.reportFailure(TechnicalAlertKind.PAYMENT_AMOUNT_MISMATCH, null, donation.providerRef, null)
        val donationId = donation.id!!
        val event = ComplianceAuditLogService.PAYMENT_AMOUNT_MISMATCH
        if (complianceAuditLogRepository.findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(donationId, event) == null) {
            complianceAuditLogService.append(
                eventType = event,
                subjectType = ComplianceAuditSubjectType.DONATION,
                payload = mapOf(
                    "providerRef" to donation.providerRef,
                    "expected" to donation.amount.toPlainString(),
                    "paid" to paid?.toPlainString(),
                ),
                subjectId = donationId,
            )
        }
        return false
    }
}

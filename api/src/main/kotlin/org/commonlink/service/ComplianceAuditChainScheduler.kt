package org.commonlink.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Runs [ComplianceAuditLogService.verifyChain] on a schedule and raises a technical alert when the chain is broken
 * or cannot be checked.
 *
 * The hash chain only makes an alteration *detectable*; until now nothing ever ran the check outside tests, so an
 * altered journal went unnoticed (audit 2026-11-12, H6). A first pass `app.compliance.audit-chain-check.initial-delay-ms`
 * after startup, then one every `delay-ms` (24 hours, fixed delay between the end of a pass and the start of the next).
 * Disabled with `app.compliance.audit-chain-check.enabled=false` (test profile).
 *
 * The check proves internal consistency only: the hash is unkeyed, so a writer able to rewrite every row from the
 * altered one onwards would go undetected. Deleting the newest rows is not detected either.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.compliance.audit-chain-check",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class ComplianceAuditChainScheduler(
    private val auditLog: ComplianceAuditLogService,
    private val alerts: TechnicalAlertService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Verifies the whole chain once; alerts on the first broken row, or when the verification itself fails. */
    @Scheduled(
        initialDelayString = "\${app.compliance.audit-chain-check.initial-delay-ms:600000}",
        fixedDelayString = "\${app.compliance.audit-chain-check.delay-ms:86400000}",
    )
    fun verify() {
        val brokenAt = try {
            auditLog.verifyChain()
        } catch (ex: Exception) {
            log.error("Compliance audit log chain could not be verified", ex)
            alerts.reportFailure(TechnicalAlertKind.COMPLIANCE_AUDIT_CHAIN_BROKEN, null, null, ex)
            return
        }
        if (brokenAt == null) {
            log.info("Compliance audit log chain verified: intact")
            return
        }
        log.error("Compliance audit log chain broken at sequence_no {}", brokenAt)
        alerts.reportFailure(
            TechnicalAlertKind.COMPLIANCE_AUDIT_CHAIN_BROKEN, null, "compliance_audit_log sequence_no=$brokenAt", null,
        )
    }
}

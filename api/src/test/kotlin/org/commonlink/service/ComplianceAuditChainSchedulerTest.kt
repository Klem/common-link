package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/** Covers [ComplianceAuditChainScheduler]: the scheduled check alerts on a broken or unverifiable chain only. */
class ComplianceAuditChainSchedulerTest {

    private val auditLog = mockk<ComplianceAuditLogService>()
    private val alerts = mockk<TechnicalAlertService>()
    private val scheduler = ComplianceAuditChainScheduler(auditLog, alerts)

    init {
        justRun { alerts.reportFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `an intact chain raises no alert`() {
        every { auditLog.verifyChain() } returns null

        scheduler.verify()

        verify(exactly = 0) { alerts.reportFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `a broken chain raises a technical alert naming the first broken row`() {
        every { auditLog.verifyChain() } returns 42L

        scheduler.verify()

        verify(exactly = 1) {
            alerts.reportFailure(
                TechnicalAlertKind.COMPLIANCE_AUDIT_CHAIN_BROKEN, null, "compliance_audit_log sequence_no=42", null,
            )
        }
    }

    @Test
    fun `a verification that cannot run raises a technical alert too`() {
        val failure = IllegalStateException("database unreachable")
        every { auditLog.verifyChain() } throws failure

        scheduler.verify()

        verify(exactly = 1) {
            alerts.reportFailure(TechnicalAlertKind.COMPLIANCE_AUDIT_CHAIN_BROKEN, null, null, failure)
        }
    }
}

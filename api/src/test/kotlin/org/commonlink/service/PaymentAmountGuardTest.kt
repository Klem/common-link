package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.Donation
import org.commonlink.repository.ComplianceAuditLogRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class PaymentAmountGuardTest {

    private val alerts = mockk<TechnicalAlertService>(relaxed = true)
    private val auditLog = mockk<ComplianceAuditLogService>(relaxed = true)
    private val auditLogRepo = mockk<ComplianceAuditLogRepository>()
    private val guard = PaymentAmountGuard(alerts, auditLog, auditLogRepo)
    private val donationId = UUID.randomUUID()
    private val donation = mockk<Donation> {
        every { id } returns donationId
        every { amount } returns BigDecimal("25.50")
        every { providerRef } returns "helloasso:4242"
    }

    @Test
    fun `the same amount matches, whatever its scale, and reports nothing`() {
        assertTrue(guard.matches(donation, BigDecimal("25.5")))
        assertTrue(guard.matches(donation, BigDecimal("25.500")))
        verify(exactly = 0) { alerts.reportFailure(any(), any(), any(), any()) }
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `another or a missing amount is reported, journaled the first time only`() {
        every { auditLogRepo.findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(donationId, any()) } returns null
        assertFalse(guard.matches(donation, BigDecimal("25.00")))
        every { auditLogRepo.findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(donationId, any()) } returns mockk<ComplianceAuditLog>()
        assertFalse(guard.matches(donation, null))

        verify(exactly = 2) { alerts.reportFailure(TechnicalAlertKind.PAYMENT_AMOUNT_MISMATCH, null, "helloasso:4242", null) }
        verify(exactly = 1) {
            auditLog.append(
                ComplianceAuditLogService.PAYMENT_AMOUNT_MISMATCH, any(),
                mapOf("providerRef" to "helloasso:4242", "expected" to "25.50", "paid" to "25.00"), donationId, any(),
            )
        }
    }
}

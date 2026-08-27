package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertDecision
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertStatus
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.ComplianceAlertRepository
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [ComplianceAlertService].
 *
 * Covers the acceptance criteria from prompt 16:
 * - Idempotency: a second createOrIgnore on the same (origin, subject) returns the existing alert
 *   without writing a second ALERT_OPENED journal entry.
 * - Status transitions: only PENDING→IN_REVIEW and IN_REVIEW→CLOSED are allowed.
 * - Audit trail: each state change writes exactly one event to [ComplianceAuditLogService].
 * - System-level (null subject_id) idempotency: SYNC_FAILURE deduplication works with null.
 */
class ComplianceAlertServiceTest {

    private val repo: ComplianceAlertRepository = mockk()
    private val auditLog: ComplianceAuditLogService = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val associationComplianceStatusService: AssociationComplianceStatusService = mockk(relaxed = true)
    private val service = ComplianceAlertService(repo, auditLog, eventPublisher, associationComplianceStatusService)

    private val associationId: UUID = UUID.randomUUID()
    private val officerId: UUID = UUID.randomUUID()
    private val alertId: UUID = UUID.randomUUID()

    private fun savedAlert(
        id: UUID = alertId,
        origin: ComplianceAlertOrigin = ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
        subjectType: ComplianceAlertSubjectType = ComplianceAlertSubjectType.ASSOCIATION,
        subjectId: UUID? = associationId,
        status: ComplianceAlertStatus = ComplianceAlertStatus.PENDING,
    ) = ComplianceAlert(
        id = id,
        origin = origin,
        subjectType = subjectType,
        subjectId = subjectId,
        severity = ComplianceAlertSeverity.HIGH,
        createdAt = Instant.now(),
        status = status,
    )

    // ─── createOrIgnore — idempotency ────────────────────────────────────────────

    @Test
    fun `createOrIgnore creates alert and writes ALERT_OPENED when no open alert exists`() {
        val alert = savedAlert()
        every { repo.findByOriginAndSubjectIdAndStatusIn(any(), any(), any()) } returns null
        every { repo.save(any<ComplianceAlert>()) } returns alert

        service.createOrIgnore(
            ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
            ComplianceAlertSubjectType.ASSOCIATION,
            associationId,
            ComplianceAlertSeverity.HIGH,
        )

        verify(exactly = 1) {
            auditLog.append(
                eventType = eq(ComplianceAuditLogService.ALERT_OPENED),
                subjectType = eq(ComplianceAuditSubjectType.ALERT),
                subjectId = eq(alert.id),
                payload = any(),
                actorUserId = isNull(),
            )
        }
    }

    @Test
    fun `createOrIgnore returns existing alert and writes NO journal entry when open alert exists`() {
        val existing = savedAlert(status = ComplianceAlertStatus.PENDING)
        every {
            repo.findByOriginAndSubjectIdAndStatusIn(
                ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING, associationId, any(),
            )
        } returns existing

        val result = service.createOrIgnore(
            ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
            ComplianceAlertSubjectType.ASSOCIATION,
            associationId,
            ComplianceAlertSeverity.HIGH,
        )

        assertSame(existing, result)
        verify(exactly = 0) { repo.save(any<ComplianceAlert>()) }
        verify(exactly = 0) { auditLog.append(any(), any(), any<Any>(), any(), any()) }
    }

    @Test
    fun `createOrIgnore with null subject_id uses IsNull repo query for SYNC_FAILURE idempotency`() {
        val existing = savedAlert(subjectId = null, origin = ComplianceAlertOrigin.SYNC_FAILURE, subjectType = ComplianceAlertSubjectType.SYSTEM)
        every {
            repo.findByOriginAndSubjectIdIsNullAndStatusIn(ComplianceAlertOrigin.SYNC_FAILURE, any())
        } returns existing

        val result = service.createOrIgnore(
            ComplianceAlertOrigin.SYNC_FAILURE,
            ComplianceAlertSubjectType.SYSTEM,
            null,
            ComplianceAlertSeverity.MEDIUM,
        )

        assertSame(existing, result)
        verify(exactly = 0) { repo.save(any<ComplianceAlert>()) }
    }

    // ─── takeInCharge ────────────────────────────────────────────────────────────

    @Test
    fun `takeInCharge sets IN_REVIEW and writes ALERT_IN_REVIEW journal entry`() {
        val alert = savedAlert(status = ComplianceAlertStatus.PENDING)
        every { repo.findById(alertId) } returns Optional.of(alert)
        every { repo.save(any<ComplianceAlert>()) } answers { firstArg() }

        service.takeInCharge(alertId, officerId)

        assertEquals(ComplianceAlertStatus.IN_REVIEW, alert.status)
        assertEquals(officerId, alert.takenInChargeBy)
        verify(exactly = 1) {
            auditLog.append(
                eventType = eq(ComplianceAuditLogService.ALERT_IN_REVIEW),
                subjectType = eq(ComplianceAuditSubjectType.ALERT),
                subjectId = eq(alertId),
                actorUserId = eq(officerId),
                payload = any(),
            )
        }
    }

    // ─── close ───────────────────────────────────────────────────────────────────

    @Test
    fun `close sets CLOSED with decision and writes ALERT_CLOSED journal entry`() {
        val alert = savedAlert(status = ComplianceAlertStatus.IN_REVIEW)
        every { repo.findById(alertId) } returns Optional.of(alert)
        every { repo.save(any<ComplianceAlert>()) } answers { firstArg() }

        service.close(alertId, officerId, ComplianceAlertDecision.FALSE_POSITIVE, "Homonymie confirmée")

        assertEquals(ComplianceAlertStatus.CLOSED, alert.status)
        assertEquals(ComplianceAlertDecision.FALSE_POSITIVE, alert.decision)
        assertEquals("Homonymie confirmée", alert.decisionRationale)
        verify(exactly = 1) {
            auditLog.append(
                eventType = eq(ComplianceAuditLogService.ALERT_CLOSED),
                subjectType = eq(ComplianceAuditSubjectType.ALERT),
                subjectId = eq(alertId),
                actorUserId = eq(officerId),
                payload = any(),
            )
        }
    }

    @Test
    fun `close rejects blank rationale`() {
        assertThrows<UnprocessableEntityException> {
            service.close(alertId, officerId, ComplianceAlertDecision.LEGITIMATE, "   ")
        }
        verify(exactly = 0) { repo.findById(any()) }
    }

    @Test
    fun `close rejects SUSPICIOUS decision without treasury fields for a freeze-origin alert`() {
        val alert = savedAlert(origin = ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING, status = ComplianceAlertStatus.IN_REVIEW)
        every { repo.findById(alertId) } returns Optional.of(alert)

        assertThrows<UnprocessableEntityException> {
            service.close(alertId, officerId, ComplianceAlertDecision.SUSPICIOUS, "Match confirmé")
        }
        verify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `close with SUSPICIOUS decision on a CAMPAIGN_REPORT alert does not require treasury fields and suspends the association`() {
        val alert = savedAlert(origin = ComplianceAlertOrigin.CAMPAIGN_REPORT, status = ComplianceAlertStatus.IN_REVIEW)
        every { repo.findById(alertId) } returns Optional.of(alert)
        every { repo.save(any<ComplianceAlert>()) } answers { firstArg() }

        service.close(alertId, officerId, ComplianceAlertDecision.SUSPICIOUS, "Signalement fondé")

        assertEquals(ComplianceAlertStatus.CLOSED, alert.status)
        verify(exactly = 1) { associationComplianceStatusService.suspend(associationId) }
        verify(exactly = 0) { associationComplianceStatusService.clearAlertIfNoneOpen(any()) }
    }

    @Test
    fun `close with LEGITIMATE decision on a CAMPAIGN_REPORT alert clears the association alert flag`() {
        val alert = savedAlert(origin = ComplianceAlertOrigin.CAMPAIGN_REPORT, status = ComplianceAlertStatus.IN_REVIEW)
        every { repo.findById(alertId) } returns Optional.of(alert)
        every { repo.save(any<ComplianceAlert>()) } answers { firstArg() }

        service.close(alertId, officerId, ComplianceAlertDecision.LEGITIMATE, "Signalement infondé")

        verify(exactly = 1) { associationComplianceStatusService.clearAlertIfNoneOpen(associationId) }
        verify(exactly = 0) { associationComplianceStatusService.suspend(any()) }
    }

    @Test
    fun `close with SUSPICIOUS decision and all treasury fields succeeds and persists fields`() {
        val alert = savedAlert(status = ComplianceAlertStatus.IN_REVIEW)
        every { repo.findById(alertId) } returns Optional.of(alert)
        every { repo.save(any<ComplianceAlert>()) } answers { firstArg() }

        val notifiedAt = java.time.Instant.now()
        service.close(
            alertId, officerId, ComplianceAlertDecision.SUSPICIOUS, "Match avéré",
            treasuryNotifiedAt = notifiedAt,
            treasuryNotificationMethod = "courriel sécurisé",
            treasuryNotificationRef = "REF-001",
        )

        assertEquals(notifiedAt, alert.treasuryNotifiedAt)
        assertEquals("courriel sécurisé", alert.treasuryNotificationMethod)
        assertEquals("REF-001", alert.treasuryNotificationRef)
    }

    // ─── validateStatusTransition ─────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1} is allowed")
    @CsvSource(
        "PENDING, IN_REVIEW",
        "IN_REVIEW, CLOSED",
    )
    fun `allowed transitions do not throw`(current: ComplianceAlertStatus, next: ComplianceAlertStatus) {
        service.validateStatusTransition(current, next)
    }

    @ParameterizedTest(name = "{0} → {1} is forbidden")
    @CsvSource(
        "PENDING, CLOSED",
        "PENDING, PENDING",
        "IN_REVIEW, PENDING",
        "IN_REVIEW, IN_REVIEW",
        "CLOSED, PENDING",
        "CLOSED, IN_REVIEW",
        "CLOSED, CLOSED",
    )
    fun `forbidden transitions throw UnprocessableEntityException`(
        current: ComplianceAlertStatus,
        next: ComplianceAlertStatus,
    ) {
        assertThrows<UnprocessableEntityException> {
            service.validateStatusTransition(current, next)
        }
    }
}

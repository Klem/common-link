package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertStatus
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.ComplianceAlertRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [AssociationComplianceStatusService] — the sole writer of
 * [org.commonlink.entity.AssociationProfile.status] (IC-44). Every transition is derived from the
 * current state, never assigned unconditionally: these tests lock in that no transition regresses
 * a state it shouldn't.
 */
class AssociationComplianceStatusServiceTest {

    private val associationProfileRepository: AssociationProfileRepository = mockk()
    private val complianceAlertRepository: ComplianceAlertRepository = mockk()
    private val auditLog: ComplianceAuditLogService = mockk(relaxed = true)
    private val service = AssociationComplianceStatusService(associationProfileRepository, complianceAlertRepository, auditLog)

    private val associationId: UUID = UUID.randomUUID()
    private val officerId: UUID = UUID.randomUUID()

    private fun association(status: AssociationStatus) = AssociationProfile(
        id = associationId,
        user = User(email = "asso@example.org", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL),
        name = "Association test",
        identifier = "W123456789",
        status = status,
    )

    private fun stubFind(status: AssociationStatus) {
        every { associationProfileRepository.findById(associationId) } returns Optional.of(association(status))
        every { associationProfileRepository.save(any<AssociationProfile>()) } answers { firstArg() }
    }

    // ─── raiseAlert ─────────────────────────────────────────────────────────────

    @Test
    fun `raiseAlert moves ACTIVE to ALERT`() {
        stubFind(AssociationStatus.ACTIVE)
        service.raiseAlert(associationId)
        verify(exactly = 1) {
            associationProfileRepository.save(match { it.status == AssociationStatus.ALERT })
        }
    }

    @Test
    fun `raiseAlert is a no-op when already ALERT`() {
        stubFind(AssociationStatus.ALERT)
        service.raiseAlert(associationId)
        verify(exactly = 0) { associationProfileRepository.save(any()) }
    }

    @Test
    fun `raiseAlert never overwrites SUSPENDED`() {
        stubFind(AssociationStatus.SUSPENDED)
        service.raiseAlert(associationId)
        verify(exactly = 0) { associationProfileRepository.save(any()) }
    }

    // ─── suspend ────────────────────────────────────────────────────────────────

    @Test
    fun `suspend sets SUSPENDED unconditionally`() {
        stubFind(AssociationStatus.ALERT)
        service.suspend(associationId)
        verify(exactly = 1) {
            associationProfileRepository.save(match { it.status == AssociationStatus.SUSPENDED })
        }
    }

    // ─── clearAlertIfNoneOpen ───────────────────────────────────────────────────

    @Test
    fun `clearAlertIfNoneOpen moves ALERT to ACTIVE when no other open report remains`() {
        stubFind(AssociationStatus.ALERT)
        every {
            complianceAlertRepository.findByOriginAndSubjectIdAndStatusIn(
                ComplianceAlertOrigin.CAMPAIGN_REPORT, associationId, any(),
            )
        } returns null

        service.clearAlertIfNoneOpen(associationId)

        verify(exactly = 1) {
            associationProfileRepository.save(match { it.status == AssociationStatus.ACTIVE })
        }
    }

    @Test
    fun `clearAlertIfNoneOpen stays ALERT when another report is still open`() {
        stubFind(AssociationStatus.ALERT)
        val stillOpen = ComplianceAlert(
            origin = ComplianceAlertOrigin.CAMPAIGN_REPORT,
            subjectType = ComplianceAlertSubjectType.ASSOCIATION,
            subjectId = associationId,
            severity = ComplianceAlertSeverity.MEDIUM,
            status = ComplianceAlertStatus.PENDING,
            createdAt = Instant.now(),
        )
        every {
            complianceAlertRepository.findByOriginAndSubjectIdAndStatusIn(
                ComplianceAlertOrigin.CAMPAIGN_REPORT, associationId, any(),
            )
        } returns stillOpen

        service.clearAlertIfNoneOpen(associationId)

        verify(exactly = 0) { associationProfileRepository.save(any()) }
    }

    @Test
    fun `clearAlertIfNoneOpen is a no-op outside ALERT — never lifts a SUSPENDED association`() {
        stubFind(AssociationStatus.SUSPENDED)
        service.clearAlertIfNoneOpen(associationId)
        verify(exactly = 0) { associationProfileRepository.save(any()) }
        verify(exactly = 0) { complianceAlertRepository.findByOriginAndSubjectIdAndStatusIn(any(), any(), any()) }
    }

    // ─── reactivate ─────────────────────────────────────────────────────────────

    @Test
    fun `reactivate rejects blank rationale`() {
        assertThrows<UnprocessableEntityException> {
            service.reactivate(associationId, officerId, "   ")
        }
        verify(exactly = 0) { associationProfileRepository.findById(any()) }
    }

    @Test
    fun `reactivate rejects an association that is not SUSPENDED`() {
        stubFind(AssociationStatus.ACTIVE)
        assertThrows<UnprocessableEntityException> {
            service.reactivate(associationId, officerId, "Contestation reçue et vérifiée")
        }
        verify(exactly = 0) { associationProfileRepository.save(any()) }
    }

    @Test
    fun `reactivate throws NotFoundException for an unknown association`() {
        every { associationProfileRepository.findById(associationId) } returns Optional.empty()
        assertThrows<NotFoundException> {
            service.reactivate(associationId, officerId, "Contestation reçue et vérifiée")
        }
    }

    @Test
    fun `reactivate moves SUSPENDED to ACTIVE and journals ASSOCIATION_REACTIVATED`() {
        stubFind(AssociationStatus.SUSPENDED)

        service.reactivate(associationId, officerId, "Contestation reçue et vérifiée")

        verify(exactly = 1) {
            associationProfileRepository.save(match { it.status == AssociationStatus.ACTIVE })
        }
        verify(exactly = 1) {
            auditLog.append(
                eventType = eq(ComplianceAuditLogService.ASSOCIATION_REACTIVATED),
                subjectType = eq(org.commonlink.entity.ComplianceAuditSubjectType.ASSOCIATION),
                subjectId = eq(associationId),
                actorUserId = eq(officerId),
                payload = any(),
            )
        }
    }
}

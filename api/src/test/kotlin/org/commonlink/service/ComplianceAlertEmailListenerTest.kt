package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.event.ComplianceAlertOpenedEvent
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Unit tests for [ComplianceAlertEmailListener].
 *
 * Covers the four properties that matter for the control:
 *  - a donor freeze alert notifies the compliance mailbox, with a usable deep link;
 *  - every other origin is silent (the onboarding path already has a curator watching);
 *  - an unconfigured mailbox degrades to a warning instead of throwing;
 *  - an SMTP failure is swallowed — the notification is best-effort, the alert row is the record.
 *
 * Runs without a Spring context.
 */
class ComplianceAlertEmailListenerTest {

    private val emailService: EmailService = mockk(relaxed = true)
    private val alertId: UUID = UUID.randomUUID()
    private val donorId: UUID = UUID.randomUUID()

    private fun listener(
        recipient: String = "compliance@common-link.org",
        frontendUrl: String = "http://localhost:3000",
    ) = ComplianceAlertEmailListener(emailService, recipient, frontendUrl)

    private fun event(
        origin: ComplianceAlertOrigin = ComplianceAlertOrigin.FREEZE_HIT_DONATION,
        severity: ComplianceAlertSeverity = ComplianceAlertSeverity.HIGH,
    ) = ComplianceAlertOpenedEvent(
        alertId = alertId,
        origin = origin,
        subjectType = ComplianceAlertSubjectType.DONOR,
        subjectId = donorId,
        severity = severity,
    )

    @Test
    fun `donor freeze alert notifies the compliance mailbox with a deep link`() {
        listener().onAlertOpened(event())

        verify(exactly = 1) {
            emailService.sendDonorFreezeAlertOpened(
                recipientEmail = "compliance@common-link.org",
                alertId = alertId,
                severity = "HIGH",
                alertUrl = "http://localhost:3000/fr/compliance/alerts/$alertId",
            )
        }
    }

    @Test
    fun `a trailing slash on the frontend url does not produce a double slash`() {
        listener(frontendUrl = "https://app.common-link.org/").onAlertOpened(event())

        verify(exactly = 1) {
            emailService.sendDonorFreezeAlertOpened(
                recipientEmail = any(),
                alertId = alertId,
                severity = any(),
                alertUrl = "https://app.common-link.org/fr/compliance/alerts/$alertId",
            )
        }
    }

    @Test
    fun `onboarding freeze alert sends nothing`() {
        listener().onAlertOpened(event(origin = ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING))

        verify(exactly = 0) {
            emailService.sendDonorFreezeAlertOpened(any(), any(), any(), any())
        }
    }

    @Test
    fun `screening unavailable and sync failure send nothing`() {
        val listener = listener()
        listener.onAlertOpened(event(origin = ComplianceAlertOrigin.SCREENING_UNAVAILABLE))
        listener.onAlertOpened(event(origin = ComplianceAlertOrigin.SYNC_FAILURE))

        verify(exactly = 0) {
            emailService.sendDonorFreezeAlertOpened(any(), any(), any(), any())
        }
    }

    @Test
    fun `blank recipient sends nothing and does not throw`() {
        listener(recipient = "  ").onAlertOpened(event())

        verify(exactly = 0) {
            emailService.sendDonorFreezeAlertOpened(any(), any(), any(), any())
        }
    }

    @Test
    fun `an SMTP failure is swallowed so the donation path is never broken by it`() {
        every {
            emailService.sendDonorFreezeAlertOpened(any(), any(), any(), any())
        } throws RuntimeException("smtp down")

        // No assertion needed beyond the absence of a propagated exception: the listener runs on the
        // freeze-screening path, whose outcome must not depend on a mail server.
        listener().onAlertOpened(event())
    }
}

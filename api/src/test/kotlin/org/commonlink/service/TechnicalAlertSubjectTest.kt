package org.commonlink.service

import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.TechnicalAlertProperties
import org.junit.jupiter.api.Test

/** [TechnicalAlertService.reportForSubject] throttles per kind and subject, never across subjects. */
class TechnicalAlertSubjectTest {

    private val email = mockk<EmailService>(relaxed = true)
    private val service = TechnicalAlertService(email, TechnicalAlertProperties(notificationEmail = "tech@example.org"))

    @Test
    fun `two subjects of the same kind are both e-mailed`() {
        service.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNLINKED, "association A", "2 don(s), 25.50 EUR")
        service.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNLINKED, "association B", "1 don(s), 10.00 EUR")

        verify(exactly = 1) { email.sendTechnicalAlert("tech@example.org", "ERROR", any(), match { it["Objet"] == "association A" }, null) }
        verify(exactly = 1) { email.sendTechnicalAlert("tech@example.org", "ERROR", any(), match { it["Objet"] == "association B" }, null) }
    }

    @Test
    fun `the same subject within the cooldown is e-mailed once`() {
        service.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNLINKED, "association A")
        service.reportForSubject(TechnicalAlertKind.HELLOASSO_CASHOUT_UNLINKED, "association A")

        verify(exactly = 1) { email.sendTechnicalAlert(any(), any(), any(), any(), any()) }
    }
}

package org.commonlink.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.mail.javamail.JavaMailSender
import java.util.Properties
import java.util.UUID

class SmtpEmailServiceTest {

    private val mailSender: JavaMailSender = mockk()
    private val mimeMessage: MimeMessage = mockk(relaxed = true)
    private val from = "noreply@commonlink.org"
    private val env: Environment = mockk()

    private lateinit var service: SmtpEmailService
    private lateinit var listAppender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        every { mailSender.createMimeMessage() } returns mimeMessage
        every { mailSender.send(mimeMessage) } returns Unit

        val logger = LoggerFactory.getLogger(SmtpEmailService::class.java) as Logger
        logger.level = Level.INFO
        listAppender = ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    @AfterEach
    fun tearDown() {
        val logger = LoggerFactory.getLogger(SmtpEmailService::class.java) as Logger
        logger.detachAppender(listAppender)
    }

    @Test
    fun `sendMagicLink sends a mime message to the right recipient`() {
        every { env.activeProfiles } returns arrayOf("staging")
        service = SmtpEmailService(mailSender, from, env)

        service.sendMagicLink("user@example.com", "http://localhost:3000/auth/verify-token?token=abc")

        verify(exactly = 1) { mailSender.send(mimeMessage) }
    }

    @Test
    fun `sendMagicLink calls send for each invocation`() {
        every { env.activeProfiles } returns arrayOf("staging")
        service = SmtpEmailService(mailSender, from, env)

        service.sendMagicLink("a@test.com", "http://localhost:3000/auth/verify-token?token=1")
        service.sendMagicLink("b@test.com", "http://localhost:3000/auth/verify-token?token=2")

        verify(exactly = 2) { mailSender.send(mimeMessage) }
    }

    /**
     * A magic link logs its holder in on its own — logging it in staging is an accepted debugging
     * aid ("I'm in dev, I never got the email, but I need the link"); logging it in prod would make
     * log access equivalent to account access, so it must stay off there (see the `prod` test below).
     */
    @Test
    fun `sendMagicLink logs the raw link on staging`() {
        every { env.activeProfiles } returns arrayOf("staging")
        service = SmtpEmailService(mailSender, from, env)

        service.sendMagicLink("user@example.com", "http://localhost:3000/auth/verify-token?token=abc")

        assertTrue(
            listAppender.list.any { it.formattedMessage.contains("user@example.com") && it.formattedMessage.contains("token=abc") },
            "Expected the magic link to be logged on staging"
        )
    }

    @Test
    fun `sendMagicLink does not log the raw link in prod`() {
        every { env.activeProfiles } returns arrayOf("prod")
        service = SmtpEmailService(mailSender, from, env)

        service.sendMagicLink("user@example.com", "http://localhost:3000/auth/verify-token?token=abc")

        assertFalse(
            listAppender.list.any { it.formattedMessage.contains("token=abc") },
            "The raw magic link must never reach production logs"
        )
    }

    /**
     * Every mail — association, donor, curator, compliance and technical alert — ends with the CommonLink signature,
     * exactly once (no leftover per-mail closing doubling it). Built on real [MimeMessage]s so the body can be read back.
     */
    @Test
    fun `every mail carries the CommonLink signature exactly once`() {
        every { env.activeProfiles } returns arrayOf("prod")
        val sent = mutableListOf<MimeMessage>()
        val realSender: JavaMailSender = mockk()
        every { realSender.createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())).also { sent += it } }
        every { realSender.send(any<MimeMessage>()) } returns Unit
        val smtp = SmtpEmailService(realSender, from, env)
        val to = "to@example.com"
        val url = "https://app.example.org/x"
        val sends: List<Pair<String, () -> Unit>> = listOf(
            "sendEmailVerification" to { smtp.sendEmailVerification(to, url) },
            "sendPasswordChanged" to { smtp.sendPasswordChanged(to) },
            "sendVerificationSubmittedToAdmin" to { smtp.sendVerificationSubmittedToAdmin("Asso", to) },
            "sendVerificationApprovedToAssociation" to { smtp.sendVerificationApprovedToAssociation("Asso", to) },
            "sendVerificationRejectedToAssociation" to { smtp.sendVerificationRejectedToAssociation("Asso", to, "motif") },
            "sendMagicLink" to { smtp.sendMagicLink(to, url) },
            "sendMollieOnboardingNeedsData" to { smtp.sendMollieOnboardingNeedsData("Asso", to) },
            "sendMollieOnboardingInReview" to { smtp.sendMollieOnboardingInReview("Asso", to) },
            "sendMollieConnectionBroken" to { smtp.sendMollieConnectionBroken("Asso", to, url) },
            "sendPaymentProviderChanged" to { smtp.sendPaymentProviderChanged("Asso", to, "HelloAsso", url) },
            "sendHelloAssoOnboardingReminder" to { smtp.sendHelloAssoOnboardingReminder("Asso", to, url, true) },
            "sendHelloAssoConnectionBroken" to { smtp.sendHelloAssoConnectionBroken("Asso", to, url) },
            "sendMollieOnboardingCompleted" to { smtp.sendMollieOnboardingCompleted("Asso", to) },
            "sendDonationReceipt" to { smtp.sendDonationReceipt(to, "Donor", "Asso", "2026-0001", byteArrayOf(1, 2, 3)) },
            "sendCampaignReportAlertOpened" to { smtp.sendCampaignReportAlertOpened(to, UUID.randomUUID(), url) },
            "sendDonorFreezeAlertOpened" to { smtp.sendDonorFreezeAlertOpened(to, UUID.randomUUID(), "HIGH", url) },
            "sendTechnicalAlert" to { smtp.sendTechnicalAlert(to, "ERROR", "Titre", mapOf("k" to "v"), null) },
        )
        assertEquals(
            EmailService::class.java.declaredMethods.size,
            sends.size,
            "A mail was added to EmailService: add it to this test",
        )

        sends.forEach { (name, send) ->
            send()
            val html = htmlOf(sent.last().apply { saveChanges() })
            assertEquals(1, html.split(SmtpEmailService.SIGNATURE_HTML).size - 1, "$name: signature count")
            assertEquals(1, html.split("L'équipe CommonLink</p>").size - 1, "$name: closing formula count")
            assertTrue(html.contains("Immatriculation ORIAS : 26010395"), name)
        }
    }

    /** The HTML body of [part], descending into multipart containers (the receipt mail carries an attachment). */
    private fun htmlOf(part: Part): String = when (val content = part.content) {
        is String -> if (part.isMimeType("text/html")) content else ""
        is Multipart -> (0 until content.count).joinToString("") { htmlOf(content.getBodyPart(it)) }
        else -> ""
    }
}

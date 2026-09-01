package org.commonlink.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.mail.javamail.JavaMailSender

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
}

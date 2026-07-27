package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSender

class SmtpEmailServiceTest {

    private val mailSender: JavaMailSender = mockk()
    private val mimeMessage: MimeMessage = mockk(relaxed = true)
    private val from = "noreply@commonlink.org"

    private lateinit var service: SmtpEmailService

    @BeforeEach
    fun setUp() {
        service = SmtpEmailService(mailSender, from)
        every { mailSender.createMimeMessage() } returns mimeMessage
        every { mailSender.send(mimeMessage) } returns Unit
    }

    @Test
    fun `sendMagicLink sends a mime message to the right recipient`() {
        service.sendMagicLink("user@example.com", "http://localhost:3000/auth/verify-token?token=abc")

        verify(exactly = 1) { mailSender.send(mimeMessage) }
    }

    @Test
    fun `sendMagicLink calls send for each invocation`() {
        service.sendMagicLink("a@test.com", "http://localhost:3000/auth/verify-token?token=1")
        service.sendMagicLink("b@test.com", "http://localhost:3000/auth/verify-token?token=2")

        verify(exactly = 2) { mailSender.send(mimeMessage) }
    }
}

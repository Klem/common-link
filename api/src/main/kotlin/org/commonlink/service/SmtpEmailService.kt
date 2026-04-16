package org.commonlink.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
@Profile("!local")
class SmtpEmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String
) : EmailService {

    override fun sendEmailVerification(email: String, verificationUrl: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(email)
        helper.setSubject("Vérifiez votre adresse email CommonLink")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>Cliquez sur le lien ci-dessous pour vérifier votre adresse email (valable 24 heures) :</p>
            <p><a href="$verificationUrl">$verificationUrl</a></p>
            <p>Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    override fun sendMagicLink(email: String, link: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(email)
        helper.setSubject("Votre lien de connexion CommonLink")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>Cliquez sur le lien ci-dessous pour vous connecter (valable 15 minutes) :</p>
            <p><a href="$link">$link</a></p>
            <p>Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }
}

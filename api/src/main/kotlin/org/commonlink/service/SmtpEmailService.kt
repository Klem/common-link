package org.commonlink.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
@Profile("staging", "prod")
class SmtpEmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
    @Value("\${app.mail.verification-review-to}") private val verificationReviewTo: String,
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

    override fun sendVerificationSubmittedToAdmin(associationName: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(verificationReviewTo)
        helper.setSubject("Nouveau dossier de vérification — $associationName")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>L'association <strong>$associationName</strong> vient de soumettre son dossier de vérification.</p>
            <p>Connectez-vous à l'interface d'administration CommonLink pour examiner les documents.</p>
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

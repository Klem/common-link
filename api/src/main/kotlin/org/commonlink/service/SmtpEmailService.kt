package org.commonlink.service

import jakarta.mail.util.ByteArrayDataSource
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

    override fun sendVerificationApprovedToAssociation(associationName: String, recipientEmail: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        helper.setSubject("Votre dossier de vérification a été approuvé — CommonLink")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>Nous avons le plaisir de vous informer que le dossier de vérification de l'association <strong>$associationName</strong> a été <strong>approuvé</strong>.</p>
            <p>Votre espace CommonLink est maintenant pleinement accessible. Vous pouvez dès à présent publier des campagnes et recevoir des dons certifiés.</p>
            <p>Merci pour votre confiance,<br>L'équipe CommonLink</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    override fun sendVerificationRejectedToAssociation(associationName: String, recipientEmail: String, reason: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        helper.setSubject("Votre dossier de vérification a été rejeté — CommonLink")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>Nous vous informons que le dossier de vérification de l'association <strong>$associationName</strong> a été <strong>rejeté</strong> pour le motif suivant :</p>
            <blockquote style="border-left:3px solid #ccc;margin:12px 0;padding:8px 16px;color:#555;">$reason</blockquote>
            <p>Vous pouvez corriger les documents concernés et soumettre à nouveau votre dossier depuis votre espace CommonLink.</p>
            <p>Cordialement,<br>L'équipe CommonLink</p>
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

    override fun sendDonationReceipt(
        donorEmail: String,
        donorName: String,
        associationName: String,
        receiptNumber: String,
        pdfBytes: ByteArray,
    ) {
        val message = mailSender.createMimeMessage()
        // multipart=true to support attachment
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(from)
        helper.setTo(donorEmail)
        helper.setSubject("Votre reçu fiscal $receiptNumber — $associationName")
        helper.setText(
            """
            <p>Bonjour $donorName,</p>
            <p>Veuillez trouver ci-joint votre reçu fiscal (n° $receiptNumber) pour votre don à <strong>$associationName</strong>.</p>
            <p>Ce reçu vous permet de bénéficier d'une réduction d'impôt conformément aux articles 200, 238 bis et 978 du CGI.
            Conservez-le précieusement.</p>
            <p>Merci pour votre générosité,<br>L'équipe CommonLink</p>
            """.trimIndent(),
            true
        )
        helper.addAttachment(
            "recu-fiscal-$receiptNumber.pdf",
            ByteArrayDataSource(pdfBytes, "application/pdf"),
        )
        mailSender.send(message)
    }
}

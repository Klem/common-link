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

    override fun sendPasswordChanged(email: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(email)
        helper.setSubject("Le mot de passe de votre compte CommonLink a été modifié")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>Le mot de passe de votre compte CommonLink vient d'être modifié, et toutes vos sessions
            ont été déconnectées.</p>
            <p>Si vous êtes à l'origine de ce changement, aucune action n'est nécessaire.
            Dans le cas contraire, réinitialisez immédiatement votre mot de passe et contactez-nous.</p>
            <p>L'équipe CommonLink</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    override fun sendVerificationSubmittedToAdmin(associationName: String, recipientEmail: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
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

    override fun sendMollieOnboardingNeedsData(associationName: String, recipientEmail: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        helper.setSubject("Des informations sont requises pour votre compte Mollie — $associationName")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>Mollie a besoin d'informations complémentaires pour finaliser la vérification du compte de l'association <strong>$associationName</strong>.</p>
            <p>Connectez-vous à votre espace CommonLink, puis accédez à l'onglet <strong>Compte bancaire</strong> et cliquez sur le bouton <em>Compléter mon dossier</em> pour accéder au tableau de bord Mollie et soumettre les documents requis.</p>
            <p>Cordialement,<br>L'équipe CommonLink</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    override fun sendMollieOnboardingInReview(associationName: String, recipientEmail: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        helper.setSubject("Votre dossier Mollie est en cours d'examen — $associationName")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>Bonne nouvelle ! Le dossier de vérification Mollie de l'association <strong>$associationName</strong> a bien été soumis et est <strong>en cours d'examen</strong> par les équipes Mollie.</p>
            <p>Ce processus prend généralement quelques jours ouvrés. Vous serez notifié dès que la vérification sera finalisée.</p>
            <p>Cordialement,<br>L'équipe CommonLink</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    override fun sendMollieConnectionBroken(associationName: String, recipientEmail: String, reconnectUrl: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        helper.setSubject("Action requise : votre compte Mollie n'est plus connecté — $associationName")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>L'autorisation Mollie de l'association <strong>$associationName</strong> a été refusée par Mollie.
            En conséquence, <strong>vos campagnes ne peuvent plus recevoir de dons en ligne</strong> pour le moment.</p>
            <p>Cela arrive lorsque l'accès accordé à CommonLink a été retiré depuis votre tableau de bord Mollie,
            ou lorsque le compte Mollie a été fermé. Une simple attente ne rétablira pas la connexion :
            il faut reconnecter votre compte.</p>
            <p><a href="$reconnectUrl">Reconnecter mon compte Mollie</a></p>
            <p>Vos campagnes et vos dons déjà reçus ne sont pas affectés : seule la collecte de nouveaux dons est suspendue.</p>
            <p>Merci de votre réactivité,<br>L'équipe CommonLink</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    override fun sendMollieOnboardingCompleted(associationName: String, recipientEmail: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        helper.setSubject("Votre compte Mollie est activé — $associationName")
        helper.setText(
            """
            <p>Bonjour,</p>
            <p>Félicitations ! La vérification KYC Mollie de l'association <strong>$associationName</strong> est <strong>terminée</strong> et votre compte est désormais activé pour recevoir des paiements.</p>
            <p>Vous pouvez dès à présent publier vos campagnes de dons et recevoir des contributions certifiées sur CommonLink.</p>
            <p>Merci pour votre confiance,<br>L'équipe CommonLink</p>
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

    override fun sendCampaignReportAlertOpened(
        recipientEmail: String,
        alertId: java.util.UUID,
        alertUrl: String,
    ) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        helper.setSubject("[IC-44] Signalement de campagne reçu")
        helper.setText(
            """
            <p>Un visiteur a signalé le contenu d'une campagne via le formulaire public.</p>
            <p>Cette alerte attend une décision de la fonction conformité.</p>
            <p>Référence de l'alerte : <strong>$alertId</strong></p>
            <p><a href="$alertUrl">Consulter l'alerte</a></p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    override fun sendDonorFreezeAlertOpened(
        recipientEmail: String,
        alertId: java.util.UUID,
        severity: String,
        alertUrl: String,
    ) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        // No donor name, no register entry, no score: the subject line lands in mail clients,
        // notification banners and backups — none of them access-controlled.
        helper.setSubject("[LCB-FT] Alerte gel des avoirs — donateur — sévérité $severity")
        helper.setText(
            """
            <p>Une mesure de gel des avoirs a été détectée lors du criblage d'un donateur.
            Le don a été refusé automatiquement ; aucun paiement n'a été créé.</p>
            <p>Cette alerte attend une décision de la fonction conformité.</p>
            <p>Référence de l'alerte : <strong>$alertId</strong><br>
            Sévérité : <strong>$severity</strong></p>
            <p><a href="$alertUrl">Ouvrir l'alerte dans le back-office conformité</a></p>
            <p>Le détail de la correspondance (nom criblé, entrée du registre, score) est consultable
            uniquement sur cet écran, après authentification.</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    /**
     * Renders the technical incident as a small definition table plus an optional `<pre>` stack
     * trace. HTML-escaped throughout: the context values include an exception message, which can
     * embed anything the failing code interpolated into it.
     */
    override fun sendTechnicalAlert(
        recipientEmail: String,
        severity: String,
        title: String,
        context: Map<String, String>,
        stackTrace: String?,
    ) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(recipientEmail)
        helper.setSubject("[TECHNIQUE][$severity] $title")
        val rows = context.entries.joinToString("\n") {
            "<tr><td><strong>${escapeHtml(it.key)}</strong></td><td>${escapeHtml(it.value)}</td></tr>"
        }
        val trace = stackTrace
            ?.let { "<p>Trace :</p><pre style=\"font-size:12px;overflow:auto\">${escapeHtml(it)}</pre>" }
            .orEmpty()
        helper.setText(
            """
            <p><strong>$severity</strong> — ${escapeHtml(title)}</p>
            <table cellpadding="4">$rows</table>
            $trace
            <p style="font-size:12px;color:#666">Alerte technique automatique CommonLink. Le canal est
            limité en débit : consultez les logs applicatifs pour le volume réel.</p>
            """.trimIndent(),
            true
        )
        mailSender.send(message)
    }

    /** Minimal HTML escaping for values interpolated into an HTML mail body. */
    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

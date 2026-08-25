package org.commonlink.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * Development stub for [EmailService] active on the `local` Spring profile.
 *
 * Instead of delivering emails, all messages are printed to the application log at INFO level.
 * This allows developers to obtain magic-link and verification URLs from the console without
 * configuring an SMTP server.
 */
@Service
@Profile("local", "test")
class EmailServiceStub : EmailService {

    private val logger = LoggerFactory.getLogger(EmailServiceStub::class.java)

    /** Logs the email verification URL to the console instead of sending an email. */
    override fun sendEmailVerification(email: String, verificationUrl: String) {
        logger.info("Email verification for $email: $verificationUrl")
    }

    /** Logs the magic-link URL to the console instead of sending an email. */
    override fun sendMagicLink(email: String, link: String) {
        logger.info("Magic link for $email: $link")
    }

    /** Logs the verification submission notification instead of sending an email. */
    override fun sendVerificationSubmittedToAdmin(associationName: String, recipientEmail: String) {
        logger.info("Verification dossier submitted by '$associationName' — would notify CURATOR at $recipientEmail")
    }

    /** Logs the approval notification instead of sending an email. */
    override fun sendVerificationApprovedToAssociation(associationName: String, recipientEmail: String) {
        logger.info("Verification approved for '$associationName' — would notify $recipientEmail")
    }

    /** Logs the rejection notification instead of sending an email. */
    override fun sendVerificationRejectedToAssociation(associationName: String, recipientEmail: String, reason: String) {
        logger.info("Verification rejected for '$associationName' — would notify $recipientEmail (reason: $reason)")
    }

    override fun sendMollieOnboardingNeedsData(associationName: String, recipientEmail: String) {
        logger.info("Mollie KYC needs-data for '$associationName' — would notify $recipientEmail")
    }

    override fun sendMollieOnboardingInReview(associationName: String, recipientEmail: String) {
        logger.info("Mollie KYC in-review for '$associationName' — would notify $recipientEmail")
    }

    override fun sendMollieOnboardingCompleted(associationName: String, recipientEmail: String) {
        logger.info("Mollie KYC completed for '$associationName' — would notify $recipientEmail")
    }

    override fun sendMollieConnectionBroken(associationName: String, recipientEmail: String, reconnectUrl: String) {
        logger.info(
            "Mollie connection BROKEN for '$associationName' — would notify $recipientEmail (reconnect: $reconnectUrl)"
        )
    }

    /** Logs the donation receipt details instead of sending an email with attachment. */
    override fun sendDonationReceipt(
        donorEmail: String,
        donorName: String,
        associationName: String,
        receiptNumber: String,
        pdfBytes: ByteArray,
    ) {
        logger.info(
            "Donation receipt {} for '{}' — would send PDF ({} bytes) to {}",
            receiptNumber, associationName, pdfBytes.size, donorEmail,
        )
    }

    /** Logs the password-change notification instead of sending an email. */
    override fun sendPasswordChanged(email: String) {
        logger.info("Password changed — would notify {}", email)
    }

    /** Logs the donor freeze alert notification instead of sending an email. No identity is logged. */
    override fun sendDonorFreezeAlertOpened(
        recipientEmail: String,
        alertId: java.util.UUID,
        severity: String,
        alertUrl: String,
    ) {
        logger.info(
            "Donor freeze alert {} ({}) — would notify compliance at {}: {}",
            alertId, severity, recipientEmail, alertUrl,
        )
    }

    /** Logs the technical alert instead of sending an email. The stack trace is already in the log. */
    override fun sendTechnicalAlert(
        recipientEmail: String,
        severity: String,
        title: String,
        context: Map<String, String>,
        stackTrace: String?,
    ) {
        logger.info(
            "Technical alert [{}] {} — would notify {} ({})",
            severity, title, recipientEmail, context,
        )
    }
}

package org.commonlink.service
import org.springframework.stereotype.Service

/**
 * Contract for transactional email delivery.
 *
 * Two implementations exist:
 * - [EmailServiceStub] — active on the `local` profile, logs links to stdout instead of sending emails.
 * - `SmtpEmailService` (production) — active on the `default` profile, sends real emails via SMTP.
 */
@Service
interface EmailService {
    /**
     * Sends a magic-link authentication email to the given address.
     *
     * @param email Recipient email address.
     * @param link  Full magic-link URL including the raw token and role query parameters.
     */
    fun sendMagicLink(email: String, link: String)

    /**
     * Sends an email-verification link to the given address after registration.
     *
     * @param email           Recipient email address.
     * @param verificationUrl Full URL the user must visit to verify their address.
     */
    fun sendEmailVerification(email: String, verificationUrl: String)

    /**
     * Notifies a curator that an association has submitted its verification dossier.
     *
     * @param associationName Official name of the association that submitted.
     * @param recipientEmail  Email address of the curator to notify.
     */
    fun sendVerificationSubmittedToAdmin(associationName: String, recipientEmail: String)

    /**
     * Notifies the association that its KYC dossier has been approved.
     * Recipient is the association's contact email, with fallback to the linked user email.
     *
     * @param associationName Official name of the association.
     * @param recipientEmail  Resolved email address to notify.
     */
    fun sendVerificationApprovedToAssociation(associationName: String, recipientEmail: String)

    /**
     * Notifies the association that its KYC dossier has been rejected, including the reason.
     * Recipient is the association's contact email, with fallback to the linked user email.
     *
     * @param associationName Official name of the association.
     * @param recipientEmail  Resolved email address to notify.
     * @param reason          The rejection reason provided by the curator.
     */
    fun sendVerificationRejectedToAssociation(associationName: String, recipientEmail: String, reason: String)

    /**
     * Notifies the association that Mollie requires additional information to continue KYC.
     *
     * @param associationName Official name of the association.
     * @param recipientEmail  Association's contact email.
     */
    fun sendMollieOnboardingNeedsData(associationName: String, recipientEmail: String)

    /**
     * Notifies the association that its Mollie KYC dossier is under review.
     *
     * @param associationName Official name of the association.
     * @param recipientEmail  Association's contact email.
     */
    fun sendMollieOnboardingInReview(associationName: String, recipientEmail: String)

    /**
     * Notifies the association that its Mollie KYC is complete and payments are enabled.
     *
     * @param associationName Official name of the association.
     * @param recipientEmail  Association's contact email.
     */
    fun sendMollieOnboardingCompleted(associationName: String, recipientEmail: String)

    /**
     * Sends the Cerfa 2041-RD fiscal receipt PDF to the donor after on-chain confirmation.
     *
     * @param donorEmail      Donor's email address.
     * @param donorName       Donor's full name (used in the email body).
     * @param associationName Name of the receiving association.
     * @param receiptNumber   Receipt reference, e.g. `2026-0042`.
     * @param pdfBytes        Raw PDF to attach — exact bytes whose hash is on-chain.
     */
    fun sendDonationReceipt(
        donorEmail: String,
        donorName: String,
        associationName: String,
        receiptNumber: String,
        pdfBytes: ByteArray,
    )
}

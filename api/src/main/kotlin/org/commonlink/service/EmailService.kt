package org.commonlink.service
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * Contract for transactional email delivery.
 *
 * Two implementations exist:
 * - [EmailServiceStub] — active on the `local` profile, logs links to stdout instead of sending emails.
 * - `SmtpEmailService` (production) — active on the `default` profile, sends real emails via SMTP.
 */
@Service
@Profile("!default")
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
}

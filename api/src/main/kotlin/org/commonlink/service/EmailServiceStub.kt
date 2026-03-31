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
@Profile("local")
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
}

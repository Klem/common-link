package org.commonlink.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("local")
class EmailServiceStub : EmailService {

    private val logger = LoggerFactory.getLogger(EmailServiceStub::class.java)

    override fun sendMagicLink(email: String, link: String) {
        logger.info("Magic link for $email: $link")
    }
}

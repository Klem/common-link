package org.commonlink.service

import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!default")
interface EmailService {
    fun sendMagicLink(email: String, link: String)
}

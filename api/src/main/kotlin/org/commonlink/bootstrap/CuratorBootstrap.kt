package org.commonlink.bootstrap

import org.commonlink.entity.AuthProvider
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Creates a CURATOR user on startup when `app.curator.email` and `app.curator.password`
 * are set in the environment.
 *
 * Intended for first-time provisioning on staging/production. Set the two env vars,
 * deploy once, then remove them — the user persists in the database.
 *
 * The bean is entirely absent when `app.curator.email` is not set, so there is no
 * runtime overhead in normal operation.
 */
@Component
@ConditionalOnProperty("app.curator.email")
class CuratorBootstrap(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.curator.email}") private val email: String,
    @Value("\${app.curator.password}") private val password: String,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (userRepository.findByEmail(email).isPresent) {
            logger.info("Curator user already exists, skipping bootstrap: {}", email)
            return
        }
        userRepository.save(
            User(
                email = email,
                role = UserRole.CURATOR,
                provider = AuthProvider.EMAIL,
                passwordHash = passwordEncoder.encode(password),
            )
        )
        logger.info("Curator user created: {}", email)
    }
}

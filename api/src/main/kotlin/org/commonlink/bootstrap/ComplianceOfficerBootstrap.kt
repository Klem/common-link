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
 * Creates a COMPLIANCE_OFFICER user on startup when `app.compliance-officer.email` and
 * `app.compliance-officer.password` are set in the environment.
 *
 * Intended for first-time provisioning of the designated LCB-FT compliance officer account
 * on staging/production. Set the two env vars, deploy once, then remove them — the user
 * persists in the database.
 *
 * The bean is entirely absent when `app.compliance-officer.email` is not set, so there is
 * no runtime overhead in normal operation.
 */
@Component
@ConditionalOnProperty("app.compliance-officer.email")
class ComplianceOfficerBootstrap(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.compliance-officer.email}") private val email: String,
    @Value("\${app.compliance-officer.password}") private val password: String,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (userRepository.findByEmail(email).isPresent) {
            logger.info("Compliance officer user already exists, skipping bootstrap: {}", email)
            return
        }
        userRepository.save(
            User(
                email = email,
                role = UserRole.COMPLIANCE_OFFICER,
                provider = AuthProvider.EMAIL,
                passwordHash = passwordEncoder.encode(password),
                // Compliance officers are provisioned out-of-band and have no inbox verification step,
                // so they must be created pre-verified — otherwise loginWithEmail rejects them
                // on the emailVerified guard before ever checking the password.
                emailVerified = true,
            )
        )
        logger.info("Compliance officer user created: {}", email)
    }
}

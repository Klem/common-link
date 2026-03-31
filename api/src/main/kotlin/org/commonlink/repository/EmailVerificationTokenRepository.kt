package org.commonlink.repository

import org.commonlink.entity.EmailVerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, UUID> {
    fun findByTokenHashAndUsedAtIsNull(tokenHash: String): Optional<EmailVerificationToken>
    fun countByUserIdAndCreatedAtAfter(userId: UUID, createdAt: Instant): Long
}

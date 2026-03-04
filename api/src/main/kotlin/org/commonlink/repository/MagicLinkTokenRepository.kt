package org.commonlink.repository

import org.commonlink.entity.MagicLinkToken
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface MagicLinkTokenRepository : JpaRepository<MagicLinkToken, UUID> {
    fun findByTokenHash(tokenHash: String): Optional<MagicLinkToken>
    fun countByEmailAndCreatedAtAfter(email: String, createdAt: Instant): Long
    fun findByTokenHashAndUsedAtIsNull(tokenHash: String): Optional<MagicLinkToken>
}

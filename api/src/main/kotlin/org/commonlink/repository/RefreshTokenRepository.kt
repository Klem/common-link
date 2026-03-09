package org.commonlink.repository

import org.commonlink.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>
    fun findAllByUserIdAndRevokedFalse(userId: UUID): List<RefreshToken>

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId")
    fun revokeAllByUserId(userId: UUID)
}

package org.commonlink.repository

import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): Optional<User>
    fun findByGoogleSub(googleSub: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun findByEmailIgnoreCase(email: String): Optional<User>
    fun findAllByRole(role: UserRole): List<User>
}

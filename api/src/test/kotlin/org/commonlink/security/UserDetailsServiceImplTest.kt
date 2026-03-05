package org.commonlink.security

import io.mockk.every
import io.mockk.mockk
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.Optional
import java.util.UUID

class UserDetailsServiceImplTest {

    private val userRepository: UserRepository = mockk()
    private val service = UserDetailsServiceImpl(userRepository)

    @Test
    fun `loadUserByUsername returns correct username and ROLE_DONOR authority`() {
        val userId = UUID.randomUUID()
        val user = User(id = userId, email = "donor@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL)
        every { userRepository.findById(userId) } returns Optional.of(user)

        val details = service.loadUserByUsername(userId.toString())

        assertEquals(userId.toString(), details.username)
        assertTrue(details.authorities.any { it.authority == "ROLE_DONOR" })
        assertEquals(1, details.authorities.size)
    }

    @Test
    fun `loadUserByUsername returns ROLE_ASSOCIATION authority for association user`() {
        val userId = UUID.randomUUID()
        val user = User(id = userId, email = "assoc@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.EMAIL)
        every { userRepository.findById(userId) } returns Optional.of(user)

        val details = service.loadUserByUsername(userId.toString())

        assertTrue(details.authorities.any { it.authority == "ROLE_ASSOCIATION" })
    }

    @Test
    fun `loadUserByUsername throws UsernameNotFoundException when user does not exist`() {
        val userId = UUID.randomUUID()
        every { userRepository.findById(userId) } returns Optional.empty()

        assertThrows<UsernameNotFoundException> {
            service.loadUserByUsername(userId.toString())
        }
    }

    @Test
    fun `loadUserByUsername uses passwordHash as password (empty string when null)`() {
        val userId = UUID.randomUUID()
        val user = User(id = userId, email = "test@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, passwordHash = null)
        every { userRepository.findById(userId) } returns Optional.of(user)

        val details = service.loadUserByUsername(userId.toString())

        assertEquals("", details.password)
    }

    @Test
    fun `loadUserByUsername uses actual passwordHash when set`() {
        val userId = UUID.randomUUID()
        val hash = "\$2a\$12\$hashedpassword"
        val user = User(id = userId, email = "test@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, passwordHash = hash)
        every { userRepository.findById(userId) } returns Optional.of(user)

        val details = service.loadUserByUsername(userId.toString())

        assertEquals(hash, details.password)
    }
}

package org.commonlink.security

import org.commonlink.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(userId: String): UserDetails {
        val user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow { UsernameNotFoundException("User not found: $userId") }
        return User(
            user.id!!.toString(),
            user.passwordHash ?: "",
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
        )
    }
}

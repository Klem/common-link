package org.commonlink.security

import org.commonlink.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Spring Security [UserDetailsService] implementation that loads users by their UUID.
 *
 * In this application the "username" concept is repurposed to store the user's UUID string,
 * because authentication is JWT-based rather than form-based. The [JwtAuthenticationFilter]
 * extracts the UUID from the token's `sub` claim and passes it here.
 *
 * The returned [UserDetails] carries a single `ROLE_<role>` authority derived from
 * [org.commonlink.entity.UserRole], which is used by Spring Security's access-control rules
 * in [SecurityConfig].
 */
@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository
) : UserDetailsService {

    /**
     * Loads a [UserDetails] object by the user's UUID string.
     *
     * The `username` parameter is expected to be the string representation of the user's UUID,
     * as embedded in the JWT `sub` claim by [JwtService.generateAccessToken].
     *
     * @param userId UUID of the user as a string.
     * @return [UserDetails] containing the user's UUID as username, their bcrypt password hash
     *         (or empty string for OAuth-only accounts), and their role authority.
     * @throws UsernameNotFoundException if no user with the given UUID exists in the database.
     */
    override fun loadUserByUsername(userId: String): UserDetails {
        val user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow { UsernameNotFoundException("User not found: $userId") }
        return User(
            user.id!!.toString(),
            // Password hash may be null for accounts created via Google or magic-link only.
            // An empty string is safe here because password authentication is not used in the JWT filter path.
            user.passwordHash ?: "",
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
        )
    }
}

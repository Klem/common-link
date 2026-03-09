package org.commonlink

import com.ninjasquad.springmockk.MockkBean
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtService
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AuthService
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * Smoke test — verifies the web layer + security config load correctly.
 * Uses @WebMvcTest to avoid needing Docker/Testcontainers.
 */
@WebMvcTest
@Import(SecurityConfig::class)
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000"
])
class CommonLinkApplicationTests {

    @MockkBean lateinit var authService: AuthService
    @MockkBean lateinit var jwtService: JwtService
    @MockkBean lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean lateinit var userRepository: UserRepository

    @Test
    fun contextLoads() {
    }
}

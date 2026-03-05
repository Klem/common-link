package org.commonlink.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.exception.InvalidTokenException
import org.commonlink.exception.TokenExpiredException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import java.util.UUID

class JwtAuthenticationFilterTest {

    private val jwtService: JwtService = mockk()
    private val userDetailsService: UserDetailsServiceImpl = mockk()
    private val filter = JwtAuthenticationFilter(jwtService, userDetailsService)

    private val userId = UUID.randomUUID()
    private val userDetails = User(userId.toString(), "", listOf(SimpleGrantedAuthority("ROLE_DONOR")))

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `no Authorization header - passes through without setting authentication`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request) // chain.doFilter was called
    }

    @Test
    fun `non-Bearer Authorization header - passes through without setting authentication`() {
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request)
    }

    @Test
    fun `valid token - sets authentication in SecurityContextHolder with correct principal and authorities`() {
        every { jwtService.extractUserId("valid.jwt.token") } returns userId
        every { userDetailsService.loadUserByUsername(userId.toString()) } returns userDetails

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid.jwt.token")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        val principal = auth?.principal as User
        assertEquals(userId.toString(), principal.username)
        assertTrue(auth.authorities.any { it.authority == "ROLE_DONOR" })
        assertNotNull(chain.request) // chain continued
    }

    @Test
    fun `expired token - clears context and continues chain without authentication`() {
        every { jwtService.extractUserId("expired.jwt.token") } throws TokenExpiredException()

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer expired.jwt.token")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request) // chain still proceeded
        verify(exactly = 0) { userDetailsService.loadUserByUsername(any()) }
    }

    @Test
    fun `invalid token - clears context and continues chain without authentication`() {
        every { jwtService.extractUserId("invalid.jwt.token") } throws InvalidTokenException()

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer invalid.jwt.token")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request)
    }

    @Test
    fun `valid token when context already has authentication - does not reload user`() {
        every { jwtService.extractUserId("valid.jwt.token") } returns userId
        every { userDetailsService.loadUserByUsername(userId.toString()) } returns userDetails

        // First call populates the context
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid.jwt.token")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        // Second call on same context should not call loadUserByUsername again
        val request2 = MockHttpServletRequest()
        request2.addHeader("Authorization", "Bearer valid.jwt.token")
        filter.doFilter(request2, MockHttpServletResponse(), MockFilterChain())

        verify(exactly = 1) { userDetailsService.loadUserByUsername(any()) }
    }
}

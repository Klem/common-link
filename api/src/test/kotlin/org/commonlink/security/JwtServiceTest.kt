package org.commonlink.security

import org.commonlink.entity.AuthProvider
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.InvalidTokenException
import org.commonlink.exception.TokenExpiredException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Field
import java.util.Date
import java.util.UUID
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys

class JwtServiceTest {

    private val secret = "test-secret-key-must-be-at-least-32-chars!!"
    private val jwtService = JwtService(secret)

    private val user = User(
        id = UUID.randomUUID(),
        email = "test@commonlink.org",
        role = UserRole.DONOR,
        provider = AuthProvider.EMAIL
    )

    @Test
    fun `generateAccessToken produces valid token with correct claims`() {
        val token = jwtService.generateAccessToken(user)
        val claims = jwtService.validateToken(token)

        assertEquals(user.id.toString(), claims.subject)
        assertEquals(user.email, claims["email"])
        assertEquals(user.role.name, claims["role"])
        assertNotNull(claims.issuedAt)
        assertTrue(claims.expiration.after(Date()))
    }

    @Test
    fun `extractUserId returns correct UUID`() {
        val token = jwtService.generateAccessToken(user)
        assertEquals(user.id, jwtService.extractUserId(token))
    }

    @Test
    fun `extractEmail returns correct email`() {
        val token = jwtService.generateAccessToken(user)
        assertEquals(user.email, jwtService.extractEmail(token))
    }

    @Test
    fun `extractRole returns correct role`() {
        val token = jwtService.generateAccessToken(user)
        assertEquals(user.role, jwtService.extractRole(token))
    }

    @Test
    fun `validateToken throws TokenExpiredException for expired token`() {
        val key = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
        val expiredToken = Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(Date(System.currentTimeMillis() - 60_000))
            .expiration(Date(System.currentTimeMillis() - 1_000))
            .signWith(key)
            .compact()

        assertThrows<TokenExpiredException> {
            jwtService.validateToken(expiredToken)
        }
    }

    @Test
    fun `validateToken throws InvalidTokenException for wrong signature`() {
        val wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-must-be-at-least-32-chars!!".toByteArray())
        val badToken = Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(wrongKey)
            .compact()

        assertThrows<InvalidTokenException> {
            jwtService.validateToken(badToken)
        }
    }

    @Test
    fun `validateToken throws InvalidTokenException for malformed token`() {
        assertThrows<InvalidTokenException> {
            jwtService.validateToken("not.a.valid.jwt.token")
        }
    }
}

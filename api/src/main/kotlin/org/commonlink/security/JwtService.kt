package org.commonlink.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.InvalidTokenException
import org.commonlink.exception.TokenExpiredException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${app.jwt.secret}") private val secret: String
) {
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    private val accessTokenExpirationMs = 15L * 60 * 1000 // 15 minutes

    fun generateAccessToken(user: User): String {
        val now = Date()
        return Jwts.builder()
            .subject(user.id!!.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(now)
            .expiration(Date(now.time + accessTokenExpirationMs))
            .signWith(signingKey)
            .compact()
    }

    fun validateToken(token: String): Claims {
        return try {
            Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            throw TokenExpiredException()
        } catch (e: JwtException) {
            throw InvalidTokenException()
        }
    }

    fun extractUserId(token: String): UUID =
        UUID.fromString(validateToken(token).subject)

    fun extractEmail(token: String): String =
        validateToken(token)["email"] as String

    fun extractRole(token: String): UserRole =
        UserRole.valueOf(validateToken(token)["role"] as String)
}

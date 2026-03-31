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

/**
 * Handles generation and validation of JWT access tokens.
 *
 * Access tokens are short-lived (15 minutes), signed with HS256 using the secret
 * configured in `app.jwt.secret`, and carry the user's id, email, and role as claims.
 * The raw token is sent to clients; only hashed refresh tokens are persisted in the DB.
 */
@Service
class JwtService(
    @Value("\${app.jwt.secret}") private val secret: String
) {
    // Signing key is derived from the configured secret and initialised lazily on first use.
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    private val accessTokenExpirationMs = 15L * 60 * 1000 // 15 minutes

    /**
     * Generates a signed JWT access token for the given user.
     *
     * The token embeds [User.id] as the `sub` claim, plus `email` and `role` custom claims.
     *
     * @param user The authenticated user for whom to issue the token.
     * @return Compact JWT string ready to be returned to the client.
     */
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

    /**
     * Validates a JWT string and returns its claims payload.
     *
     * Translates JJWT-specific exceptions into application-level exceptions so that
     * callers never need to depend on the JJWT library directly.
     *
     * @param token Compact JWT string from the `Authorization: Bearer` header.
     * @return Parsed [Claims] payload if the signature is valid and the token is not expired.
     * @throws TokenExpiredException if the token's expiry date has passed.
     * @throws InvalidTokenException if the token is malformed or the signature is invalid.
     */
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

    /**
     * Extracts and returns the user's [UUID] from the `sub` claim of the token.
     *
     * @param token Compact JWT string.
     * @return [UUID] of the authenticated user.
     */
    fun extractUserId(token: String): UUID =
        UUID.fromString(validateToken(token).subject)

    /**
     * Extracts the user's email address from the `email` claim of the token.
     *
     * @param token Compact JWT string.
     * @return Email address embedded in the token.
     */
    fun extractEmail(token: String): String =
        validateToken(token)["email"] as String

    /**
     * Extracts the user's [UserRole] from the `role` claim of the token.
     *
     * @param token Compact JWT string.
     * @return [UserRole] embedded in the token.
     */
    fun extractRole(token: String): UserRole =
        UserRole.valueOf(validateToken(token)["role"] as String)
}

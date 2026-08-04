package org.commonlink.service

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Issues and verifies short-lived landing page preview tokens.
 *
 * The landing page endpoint refuses a campaign that is not LIVE (409), which is correct for donors but
 * useless for the association: it customizes its page while the campaign is still a draft. A preview
 * token lifts that single check — and nothing else — for the association that owns the widget.
 *
 * Signed, never persisted: the token carries its own scope (`sub` = association profile id) and expiry,
 * so no table and no cleanup job are needed. It travels in a URL, which is why the TTL is minutes.
 */
@Service
class LandingPreviewTokenService(
    @Value("\${app.jwt.secret}") private val secret: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Marks the token's single allowed use. An access token carries no such claim, so it can never pass here. */
        const val PURPOSE = "landing-preview"

        /** Long enough to tweak a theme and look at the result, short enough for a token sitting in a URL. */
        val TTL: Duration = Duration.ofMinutes(10)

        private const val KEY_LABEL = "landing-preview-v1"
    }

    /**
     * Signing key derived from the application secret, but deliberately **not** the access-token key.
     *
     * Key separation costs one hash and removes a whole class of confusion: a preview token — which is
     * handed to the browser inside a query string — cannot be replayed as an authentication token, and
     * an access token cannot unlock a preview.
     */
    private val signingKey: SecretKey by lazy {
        val material = MessageDigest.getInstance("SHA-256")
            .digest("$KEY_LABEL|$secret".toByteArray(Charsets.UTF_8))
        Keys.hmacShaKeyFor(material)
    }

    /**
     * Issues a preview token for an association.
     *
     * @param associationId UUID of the association profile allowed to preview.
     * @return The compact token and its absolute expiry instant.
     */
    fun issue(associationId: UUID): Pair<String, Instant> {
        val now = Instant.now()
        val expiresAt = now.plus(TTL)
        val token = Jwts.builder()
            .subject(associationId.toString())
            .claim("purpose", PURPOSE)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact()
        logger.debug("Landing preview token issued for association {} (expires {})", associationId, expiresAt)
        return token to expiresAt
    }

    /**
     * Resolves the association a preview token grants access to.
     *
     * Returns null — never throws — for every rejection case (missing, malformed, wrong signature,
     * expired, wrong purpose, non-UUID subject). Callers treat null as "no preview", which keeps the
     * public endpoint's behaviour identical to a request that carried no token at all.
     *
     * @param token Compact token from the `preview` query parameter, or null.
     * @return UUID of the association allowed to preview, or null if the token grants nothing.
     */
    fun resolveAssociationId(token: String?): UUID? {
        if (token.isNullOrBlank()) return null
        return try {
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
            if (claims["purpose"] != PURPOSE) {
                logger.debug("Landing preview token rejected: wrong purpose claim")
                return null
            }
            UUID.fromString(claims.subject)
        } catch (e: JwtException) {
            logger.debug("Landing preview token rejected: {}", e.javaClass.simpleName)
            null
        } catch (e: IllegalArgumentException) {
            logger.debug("Landing preview token rejected: subject is not a UUID")
            null
        }
    }
}

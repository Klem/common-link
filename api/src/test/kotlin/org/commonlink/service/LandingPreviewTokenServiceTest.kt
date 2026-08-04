package org.commonlink.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Unit tests for [LandingPreviewTokenService] — real signing, no Spring context.
 *
 * The key derivation is mirrored here on purpose: forging an expired or mis-purposed token is the only
 * way to test the rejection paths, and doing it through the service's own API would require a
 * test-only back door. If the derivation changes, [previewKey] must change with it — that coupling is
 * the point, it keeps `accessTokenKey` provably distinct.
 */
class LandingPreviewTokenServiceTest {

    private val secret = "test-secret-key-must-be-at-least-32-chars!!"
    private val service = LandingPreviewTokenService(secret)
    private val assocId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    /** Same derivation as the service under test. */
    private val previewKey: SecretKey =
        Keys.hmacShaKeyFor(
            MessageDigest.getInstance("SHA-256")
                .digest("landing-preview-v1|$secret".toByteArray(Charsets.UTF_8))
        )

    /** The key [org.commonlink.security.JwtService] uses for access tokens: the raw secret. */
    private val accessTokenKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    @Test
    fun `issue then resolve - returns the association id`() {
        val (token, expiresAt) = service.issue(assocId)

        assertEquals(assocId, service.resolveAssociationId(token))
        assertEquals(LandingPreviewTokenService.TTL.toMinutes(), 10L)
        assertNotNull(expiresAt)
    }

    @Test
    fun `resolve - null and blank grant nothing`() {
        assertNull(service.resolveAssociationId(null))
        assertNull(service.resolveAssociationId(""))
        assertNull(service.resolveAssociationId("   "))
    }

    @Test
    fun `resolve - garbage is rejected`() {
        assertNull(service.resolveAssociationId("not-a-jwt"))
    }

    @Test
    fun `resolve - expired token is rejected`() {
        val past = Instant.now().minusSeconds(3600)
        val expired = Jwts.builder()
            .subject(assocId.toString())
            .claim("purpose", LandingPreviewTokenService.PURPOSE)
            .issuedAt(Date.from(past))
            .expiration(Date.from(past.plusSeconds(600)))
            .signWith(previewKey)
            .compact()

        assertNull(service.resolveAssociationId(expired))
    }

    @Test
    fun `resolve - correct signature but wrong purpose is rejected`() {
        val wrongPurpose = Jwts.builder()
            .subject(assocId.toString())
            .claim("purpose", "password-reset")
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(previewKey)
            .compact()

        assertNull(service.resolveAssociationId(wrongPurpose))
    }

    @Test
    fun `resolve - missing purpose claim is rejected`() {
        val noPurpose = Jwts.builder()
            .subject(assocId.toString())
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(previewKey)
            .compact()

        assertNull(service.resolveAssociationId(noPurpose))
    }

    @Test
    fun `resolve - token signed with the access-token key is rejected`() {
        // Key separation: even a perfectly shaped preview token signed with the auth key must fail, so
        // an access token can never be replayed as a preview and vice versa.
        val wrongKey = Jwts.builder()
            .subject(assocId.toString())
            .claim("purpose", LandingPreviewTokenService.PURPOSE)
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(accessTokenKey)
            .compact()

        assertNull(service.resolveAssociationId(wrongKey))
    }

    @Test
    fun `resolve - token signed with another secret is rejected`() {
        val foreign = LandingPreviewTokenService("another-secret-key-at-least-32-chars-long!!")
        val (token, _) = foreign.issue(assocId)

        assertNull(service.resolveAssociationId(token))
    }

    @Test
    fun `resolve - non-UUID subject is rejected`() {
        val badSubject = Jwts.builder()
            .subject("not-a-uuid")
            .claim("purpose", LandingPreviewTokenService.PURPOSE)
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(previewKey)
            .compact()

        assertNull(service.resolveAssociationId(badSubject))
    }
}

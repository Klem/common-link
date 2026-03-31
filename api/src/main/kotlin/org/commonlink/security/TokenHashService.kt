package org.commonlink.security

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Utility service for generating and verifying opaque tokens stored as SHA-256 hashes.
 *
 * The raw token is delivered to the client (via email or HTTP response) and is never
 * persisted. Only the SHA-256 hex digest is stored in the database, so a compromised
 * database cannot be used to replay tokens.
 *
 * Used for: refresh tokens, magic-link tokens, and email verification tokens.
 */
@Service
class TokenHashService {
    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically random 32-byte opaque token encoded as a lowercase hex string.
     *
     * The token has 256 bits of entropy, making brute-force attacks infeasible.
     *
     * @return 64-character lowercase hex string representing the raw token.
     */
    fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    /**
     * Computes the SHA-256 hex digest of the given raw token string.
     *
     * This digest is the value stored in the database. The raw token is never persisted.
     *
     * @param rawToken The plaintext token received from the client.
     * @return 64-character lowercase hex digest.
     */
    fun hashToken(rawToken: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(Charsets.UTF_8))
            .toHex()

    /**
     * Verifies that a raw token matches a previously stored hash.
     *
     * Recomputes the hash and performs a simple equality check. Note: this is not a
     * constant-time comparison; timing attacks are not a concern here because the token
     * itself already has 256 bits of entropy (guessing the input is computationally infeasible).
     *
     * @param rawToken The plaintext token received from the client.
     * @param hash The SHA-256 hex digest stored in the database.
     * @return `true` if the token matches the hash.
     */
    fun verifyToken(rawToken: String, hash: String): Boolean =
        hashToken(rawToken) == hash

    /** Encodes a byte array as a lowercase hexadecimal string. */
    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}

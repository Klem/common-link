package org.commonlink.security

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom

@Service
class TokenHashService {
    private val secureRandom = SecureRandom()

    fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    fun hashToken(rawToken: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(Charsets.UTF_8))
            .toHex()

    fun verifyToken(rawToken: String, hash: String): Boolean =
        hashToken(rawToken) == hash

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}

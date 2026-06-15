package org.commonlink.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JPA converter for Monerium OAuth tokens. When MONERIUM_TOKEN_ENC_KEY is set the tokens are
 * stored as Base64(12-byte-IV || AES-256-GCM-ciphertext). When the key is absent the converter
 * is a no-op (plaintext), which is the intended behaviour for dev and staging environments.
 *
 * Hibernate 7 + Spring Boot 4: annotated as both @Component and @Converter so Hibernate resolves
 * this bean from the Spring container, enabling constructor injection of the key.
 */
@Component
@Converter
class MoneriumTokenConverter(
    @Value("\${app.monerium.token-enc-key:}") rawKey: String
) : AttributeConverter<String, String> {

    private val secretKey: SecretKeySpec?

    init {
        if (rawKey.isBlank()) {
            secretKey = null
            logger.warn("MoneriumTokenConverter: DISABLED — MONERIUM_TOKEN_ENC_KEY not set; tokens stored as plaintext")
        } else {
            val keyBytes = try {
                Base64.getDecoder().decode(rawKey)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("app.monerium.token-enc-key is not valid Base64", e)
            }
            require(keyBytes.size == 32) {
                "app.monerium.token-enc-key must decode to exactly 32 bytes (AES-256); got ${keyBytes.size}"
            }
            secretKey = SecretKeySpec(keyBytes, "AES")
            logger.info("MoneriumTokenConverter: ACTIVE — AES-256-GCM token encryption enabled")
        }
    }

    override fun convertToDatabaseColumn(attribute: String): String {
        val key = secretKey ?: return attribute
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = newCipher()
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(attribute.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    override fun convertToEntityAttribute(dbData: String): String {
        val key = secretKey ?: return dbData
        val decoded = Base64.getDecoder().decode(dbData)
        val iv = decoded.copyOfRange(0, IV_LENGTH)
        val ciphertext = decoded.copyOfRange(IV_LENGTH, decoded.size)
        val cipher = newCipher()
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun newCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    companion object {
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private val logger = org.slf4j.LoggerFactory.getLogger(MoneriumTokenConverter::class.java)
    }
}

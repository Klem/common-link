package org.commonlink.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts the payment providers' OAuth access/refresh tokens at rest ([org.commonlink.entity.MollieConnection],
 * [org.commonlink.entity.HelloAssoConnection]), with the key `app.oauth.token-enc-key` (env `OAUTH_TOKEN_ENC_KEY`).
 *
 * Opt-in only — NOT `autoApply`: callers must annotate the field with
 * `@Convert(converter = OAuthTokenConverter::class)`. AES-256-GCM with a fresh random 12-byte IV
 * on every write; the IV is prepended to the ciphertext and the whole blob is Base64-encoded as
 * a single value.
 *
 * Mirrors [org.commonlink.entity.ComplianceCryptoConverter]'s no-op behaviour: when the key is
 * absent the converter passes values through unchanged (plaintext), acceptable for local/staging
 * only. Under the `prod` profile a blank key is a hard startup failure.
 */
@Component
@Converter
class OAuthTokenConverter(
    @Value("\${app.oauth.token-enc-key:}") rawKey: String,
    environment: Environment,
) : AttributeConverter<String?, String?> {

    private val secretKey: SecretKeySpec?

    init {
        if (rawKey.isBlank()) {
            if (environment.activeProfiles.contains("prod")) {
                throw IllegalStateException(
                    "app.oauth.token-enc-key must be set under the prod profile — " +
                        "refusing to store OAuth tokens as plaintext"
                )
            }
            secretKey = null
            if (statusLogged.compareAndSet(false, true)) {
                logger.warn("OAuthTokenConverter: DISABLED — app.oauth.token-enc-key not set; fields using this converter are stored as plaintext")
            }
        } else {
            val keyBytes = try {
                Base64.getDecoder().decode(rawKey)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("app.oauth.token-enc-key is not valid Base64", e)
            }
            require(keyBytes.size == KEY_LENGTH_BYTES) {
                "app.oauth.token-enc-key must decode to exactly $KEY_LENGTH_BYTES bytes (AES-256); got ${keyBytes.size}"
            }
            secretKey = SecretKeySpec(keyBytes, "AES")
            if (statusLogged.compareAndSet(false, true)) {
                logger.info("OAuthTokenConverter: ACTIVE — AES-256-GCM token encryption enabled")
            }
        }
    }

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val key = secretKey ?: return attribute
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = newCipher()
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(attribute.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        val key = secretKey ?: return dbData
        val ivAndCiphertext = Base64.getDecoder().decode(dbData)
        require(ivAndCiphertext.size > IV_LENGTH_BYTES) {
            "OAuthTokenConverter: stored value is too short to contain an IV — " +
                "refusing to treat unencrypted data in this column as plaintext"
        }
        val iv = ivAndCiphertext.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = ivAndCiphertext.copyOfRange(IV_LENGTH_BYTES, ivAndCiphertext.size)
        val cipher = newCipher()
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun newCipher(): Cipher = Cipher.getInstance(TRANSFORMATION)

    companion object {
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_LENGTH_BYTES = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val logger = org.slf4j.LoggerFactory.getLogger(OAuthTokenConverter::class.java)
        private val statusLogged = AtomicBoolean(false)
    }
}

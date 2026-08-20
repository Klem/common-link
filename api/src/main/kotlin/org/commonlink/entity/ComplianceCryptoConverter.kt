package org.commonlink.entity

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
 * Reusable applicative encryption primitive for LCB-FT sensitive fields.
 *
 * Opt-in only — NOT `autoApply`: callers must annotate the field with
 * `@Convert(converter = ComplianceCryptoConverter::class)`. AES-256-GCM with a fresh random
 * 12-byte IV on every write, stored as `v1:<iv_b64>:<ct_b64>`. The `v1` prefix exists so a future
 * key rotation can introduce `v2` without a data migration.
 *
 * Mirrors [org.commonlink.security.MoneriumTokenConverter]: when the key is absent the converter
 * is a no-op (plaintext), which is acceptable for local/staging only. Under the `prod` profile a
 * blank key is a hard startup failure — production never stores LCB-FT data unencrypted.
 *
 * Delivers only the primitive (Sprint 19 prompt 2) — no entity field uses it yet.
 */
@Component
@Converter
class ComplianceCryptoConverter(
    @Value("\${commonlink.compliance.encryption-key:}") rawKey: String,
    environment: Environment,
) : AttributeConverter<String?, String?> {

    private val secretKey: SecretKeySpec?

    init {
        if (rawKey.isBlank()) {
            if (environment.activeProfiles.contains("prod")) {
                throw IllegalStateException(
                    "commonlink.compliance.encryption-key must be set under the prod profile — " +
                        "refusing to store LCB-FT sensitive data as plaintext"
                )
            }
            secretKey = null
            if (statusLogged.compareAndSet(false, true)) {
                logger.warn("ComplianceCryptoConverter: DISABLED — commonlink.compliance.encryption-key not set; fields using this converter are stored as plaintext")
            }
        } else {
            val keyBytes = try {
                Base64.getDecoder().decode(rawKey)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("commonlink.compliance.encryption-key is not valid Base64", e)
            }
            require(keyBytes.size == KEY_LENGTH_BYTES) {
                "commonlink.compliance.encryption-key must decode to exactly $KEY_LENGTH_BYTES bytes (AES-256); got ${keyBytes.size}"
            }
            secretKey = SecretKeySpec(keyBytes, "AES")
            if (statusLogged.compareAndSet(false, true)) {
                logger.info("ComplianceCryptoConverter: ACTIVE — AES-256-GCM field encryption enabled")
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
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ctB64 = Base64.getEncoder().encodeToString(ciphertext)
        return "$VERSION_PREFIX:$ivB64:$ctB64"
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        val key = secretKey ?: return dbData
        val parts = dbData.split(":", limit = 3)
        require(parts.size == 3 && parts[0] == VERSION_PREFIX) {
            "ComplianceCryptoConverter: stored value does not carry the expected \"$VERSION_PREFIX:\" " +
                "prefix — refusing to treat unencrypted data in this column as plaintext"
        }
        val iv = Base64.getDecoder().decode(parts[1])
        val ciphertext = Base64.getDecoder().decode(parts[2])
        val cipher = newCipher()
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun newCipher(): Cipher = Cipher.getInstance(TRANSFORMATION)

    companion object {
        private const val VERSION_PREFIX = "v1"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_LENGTH_BYTES = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val logger = org.slf4j.LoggerFactory.getLogger(ComplianceCryptoConverter::class.java)
        private val statusLogged = AtomicBoolean(false)
    }
}

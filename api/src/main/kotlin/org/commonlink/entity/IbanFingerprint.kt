package org.commonlink.entity

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Deterministic fingerprint of an IBAN, used only to detect duplicates on `payee_ibans`
 * (security audit 2026-10-06, finding #2).
 *
 * [ComplianceCryptoConverter] encrypts `iban` with a fresh random IV on every write, so two
 * encryptions of the same IBAN produce different ciphertexts -- the `payee_ibans_unique`
 * constraint compares this fingerprint instead, never the encrypted column. HMAC-SHA256 over a
 * subkey derived from `commonlink.compliance.encryption-key` (never the raw key itself, so a
 * fingerprint leak can't help decrypt `iban`), hex-encoded to fit `VARCHAR(64)` exactly.
 *
 * Mirrors [ComplianceCryptoConverter]'s no-op behaviour when the key is absent: the IBAN itself
 * is used as its own "fingerprint" (still unique, just not hardened) -- acceptable for
 * local/staging only, same as the converter.
 */
@Component
class IbanFingerprint(
    @Value("\${commonlink.compliance.encryption-key:}") rawKey: String,
) {
    private val subKey: ByteArray? = if (rawKey.isBlank()) {
        null
    } else {
        hmac(Base64.getDecoder().decode(rawKey), SUBKEY_CONTEXT)
    }

    /** Hex-encoded HMAC-SHA256 of [normalisedIban] (already uppercased/stripped of spaces). */
    fun of(normalisedIban: String): String {
        val key = subKey ?: return normalisedIban
        return hmac(key, normalisedIban.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    companion object {
        private val SUBKEY_CONTEXT = "payee-iban-fingerprint-v1".toByteArray(Charsets.UTF_8)
    }
}

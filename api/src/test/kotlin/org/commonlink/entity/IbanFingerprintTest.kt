package org.commonlink.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IbanFingerprintTest {

    // 32 zero bytes encoded as Base64 — valid 256-bit key, same convention as ComplianceCryptoConverterTest
    private val validKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val fingerprint = IbanFingerprint(validKey)

    @Test
    fun `same IBAN always yields the same fingerprint`() {
        val iban = "FR7630006000011234567890189"
        assertThat(fingerprint.of(iban)).isEqualTo(fingerprint.of(iban))
    }

    @Test
    fun `different IBANs yield different fingerprints`() {
        assertThat(fingerprint.of("FR7630006000011234567890189"))
            .isNotEqualTo(fingerprint.of("DE89370400440532013000"))
    }

    @Test
    fun `fingerprint is hex-encoded SHA-256 length (64 chars) and fits VARCHAR(64)`() {
        assertThat(fingerprint.of("FR7630006000011234567890189")).hasSize(64).matches("[0-9a-f]+")
    }

    @Test
    fun `a different key yields a different fingerprint for the same IBAN`() {
        val otherKey = Base64Of(ByteArray(32) { 1 })
        val other = IbanFingerprint(otherKey)
        val iban = "FR7630006000011234567890189"
        assertThat(fingerprint.of(iban)).isNotEqualTo(other.of(iban))
    }

    // ── no-op mode (dev/staging — no encryption key configured) ───────────────

    @Test
    fun `blank key falls back to the IBAN itself, same convention as ComplianceCryptoConverter`() {
        val noOp = IbanFingerprint("")
        assertThat(noOp.of("FR7630006000011234567890189")).isEqualTo("FR7630006000011234567890189")
    }

    private fun Base64Of(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)
}

package org.commonlink.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.util.Base64
import kotlin.experimental.xor

class ComplianceCryptoConverterTest {

    // No active profiles → not prod, so a blank key is a no-op rather than a hard failure.
    private val nonProdEnv = MockEnvironment()

    // 32 zero bytes encoded as Base64 — valid 256-bit key
    private val validKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val converter = ComplianceCryptoConverter(validKey, nonProdEnv)

    @Test
    fun `round-trip preserves plaintext`() {
        val plaintext = "12 rue de l'Église — n°34, café, señor, 🎉"
        val encrypted = converter.convertToDatabaseColumn(plaintext)
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo(plaintext)
    }

    @Test
    fun `round-trip preserves empty string`() {
        val encrypted = converter.convertToDatabaseColumn("")
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo("")
    }

    @Test
    fun `null in gives null out on write`() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
    }

    @Test
    fun `null in gives null out on read`() {
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `stored value carries the v1 prefix`() {
        val encrypted = converter.convertToDatabaseColumn("some-sensitive-value")
        assertThat(encrypted).startsWith("v1:")
    }

    @Test
    fun `two encryptions of the same value differ due to random IV`() {
        val plaintext = "same-value-twice"
        val first = converter.convertToDatabaseColumn(plaintext)
        val second = converter.convertToDatabaseColumn(plaintext)
        assertThat(first).isNotEqualTo(second)
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(plaintext)
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(plaintext)
    }

    @Test
    fun `tampering with the ciphertext fails decryption instead of silently corrupting it`() {
        val encrypted = converter.convertToDatabaseColumn("integrity-check")
        val parts = encrypted!!.split(":")
        val ctBytes = Base64.getDecoder().decode(parts[2]).also { it[0] = it[0].xor(0x01) }
        val tampered = "${parts[0]}:${parts[1]}:${Base64.getEncoder().encodeToString(ctBytes)}"
        assertThatThrownBy { converter.convertToEntityAttribute(tampered) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `missing v1 prefix is rejected with a clear exception`() {
        assertThatThrownBy { converter.convertToEntityAttribute("plain-unencrypted-value") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("v1:")
    }

    @Test
    fun `short key (less than 32 bytes after decode) is rejected`() {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16)) // 128-bit — too short
        assertThatThrownBy { ComplianceCryptoConverter(shortKey, nonProdEnv) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("32 bytes")
    }

    @Test
    fun `invalid Base64 key is rejected`() {
        assertThatThrownBy { ComplianceCryptoConverter("not-valid-base64!!!", nonProdEnv) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not valid Base64")
    }

    // ── no-op mode (dev/staging — no key configured) ──────────────────────────

    private val noOpConverter = ComplianceCryptoConverter("", nonProdEnv)

    @Test
    fun `no-op mode - write returns plaintext unchanged`() {
        assertThat(noOpConverter.convertToDatabaseColumn("raw-value")).isEqualTo("raw-value")
    }

    @Test
    fun `no-op mode - read returns value unchanged`() {
        assertThat(noOpConverter.convertToEntityAttribute("raw-value")).isEqualTo("raw-value")
    }

    @Test
    fun `no-op mode - null in gives null out`() {
        assertThat(noOpConverter.convertToDatabaseColumn(null)).isNull()
        assertThat(noOpConverter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `no-op mode - blank key does not throw at construction`() {
        ComplianceCryptoConverter("   ", nonProdEnv) // whitespace-only key also disabled
    }

    @Test
    fun `blank key under prod profile throws at construction`() {
        val prodEnv = MockEnvironment().apply { setActiveProfiles("prod") }
        assertThatThrownBy { ComplianceCryptoConverter("", prodEnv) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("prod")
    }
}

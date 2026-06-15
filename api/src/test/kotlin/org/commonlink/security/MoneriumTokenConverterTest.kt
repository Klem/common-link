package org.commonlink.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class MoneriumTokenConverterTest {

    // 32 zero bytes encoded as Base64 — valid 256-bit key
    private val validKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val converter = MoneriumTokenConverter(validKey)

    @Test
    fun `round-trip preserves plaintext`() {
        val plaintext = "eyJhbGciOiJSUzI1NiJ9.sample-access-token"
        val encrypted = converter.convertToDatabaseColumn(plaintext)
        val decrypted = converter.convertToEntityAttribute(encrypted)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `encrypted value is not equal to plaintext`() {
        val plaintext = "my-secret-refresh-token"
        val encrypted = converter.convertToDatabaseColumn(plaintext)
        assertThat(encrypted).isNotEqualTo(plaintext)
    }

    @Test
    fun `encrypted value looks like Base64`() {
        val encrypted = converter.convertToDatabaseColumn("any-token")
        assertThat(Base64.getDecoder().decode(encrypted)).isNotEmpty()
    }

    @Test
    fun `two encryptions of same input differ due to random IV`() {
        val plaintext = "same-token"
        val first = converter.convertToDatabaseColumn(plaintext)
        val second = converter.convertToDatabaseColumn(plaintext)
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `short key (less than 32 bytes after decode) is rejected`() {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16)) // 128-bit — too short
        assertThatThrownBy { MoneriumTokenConverter(shortKey) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("32 bytes")
    }

    @Test
    fun `invalid Base64 key is rejected`() {
        assertThatThrownBy { MoneriumTokenConverter("not-valid-base64!!!") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not valid Base64")
    }

    // ── no-op mode (dev/staging — no key configured) ──────────────────────────

    private val noOpConverter = MoneriumTokenConverter("")

    @Test
    fun `no-op mode - write returns plaintext unchanged`() {
        assertThat(noOpConverter.convertToDatabaseColumn("raw-token")).isEqualTo("raw-token")
    }

    @Test
    fun `no-op mode - read returns value unchanged`() {
        assertThat(noOpConverter.convertToEntityAttribute("raw-token")).isEqualTo("raw-token")
    }

    @Test
    fun `no-op mode - blank key does not throw at construction`() {
        MoneriumTokenConverter("   ") // whitespace-only key also disabled
    }
}

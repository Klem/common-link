package org.commonlink.onchain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigInteger

class OnchainCodecTest {

    private val uint96Max = BigInteger.TWO.pow(96) - BigInteger.ONE

    @Test
    fun `requireUint96 accepts zero and the uint96 maximum`() {
        assertThat(OnchainCodec.requireUint96(BigInteger.ZERO)).isEqualTo(BigInteger.ZERO)
        assertThat(OnchainCodec.requireUint96(uint96Max)).isEqualTo(uint96Max)
    }

    @Test
    fun `requireUint96 rejects a value above uint96`() {
        assertThatThrownBy { OnchainCodec.requireUint96(uint96Max + BigInteger.ONE) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("uint96")
    }

    @Test
    fun `requireUint96 rejects a negative value`() {
        assertThatThrownBy { OnchainCodec.requireUint96(BigInteger.valueOf(-1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}

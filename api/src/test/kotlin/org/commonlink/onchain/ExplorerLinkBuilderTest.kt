package org.commonlink.onchain

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.config.OnchainConfig
import org.junit.jupiter.api.Test

class ExplorerLinkBuilderTest {

    private fun builder(mock: Boolean, baseUrl: String) = ExplorerLinkBuilder(
        OnchainConfig(
            rpcUrl = "https://rpc.chiadochain.net",
            chainId = 10200,
            registryAddress = "0x00000000000000000000000000000000000000aa",
            recorderPk = "pk",
            pollingIntervalMs = 1,
            receiptTimeoutMs = 1,
            donorAddressSecret = "s",
            associationAddressSecret = "s",
            mock = mock,
            worker = OnchainConfig.WorkerConfig(enabled = false, batchSize = 1, fixedDelayMs = 1),
            explorer = OnchainConfig.ExplorerConfig(baseUrl),
        )
    )

    @Test
    fun `builds Blockscout address and tx links`() {
        val b = builder(mock = false, baseUrl = "https://gnosis-chiado.blockscout.com/")
        assertThat(b.addressUrl("0xabc")).isEqualTo("https://gnosis-chiado.blockscout.com/address/0xabc")
        assertThat(b.txUrl("0x123")).isEqualTo("https://gnosis-chiado.blockscout.com/tx/0x123")
    }

    @Test
    fun `no link in mock mode, without explorer, or without value`() {
        assertThat(builder(mock = true, baseUrl = "https://gnosis.blockscout.com").txUrl("0x123")).isNull()
        assertThat(builder(mock = false, baseUrl = "").txUrl("0x123")).isNull()
        assertThat(builder(mock = false, baseUrl = "https://gnosis.blockscout.com").addressUrl(null)).isNull()
    }
}

package org.commonlink.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class OnchainRealModeValidatorTest {

    private fun cfg(
        mock: Boolean = false,
        rpcUrl: String = "https://rpc.chiadochain.net",
        registry: String = "0x00000000000000000000000000000000000000aa",
        recorderPk: String = "0xabc",
        pauserPk: String = "0xdef",
        donorSecret: String = "a-real-donor-secret",
        assoSecret: String = "a-real-association-secret",
        chainId: Long = 10200,
    ) = OnchainConfig(
        rpcUrl = rpcUrl,
        chainId = chainId,
        registryAddress = registry,
        recorderPk = recorderPk,
        pauserPk = pauserPk,
        pollingIntervalMs = 1,
        receiptTimeoutMs = 1,
        donorAddressSecret = donorSecret,
        associationAddressSecret = assoSecret,
        mock = mock,
        worker = OnchainConfig.WorkerConfig(enabled = true, batchSize = 10, fixedDelayMs = 1),
    )

    @Test
    fun `a valid real configuration passes`() {
        assertDoesNotThrow { OnchainRealModeValidator(cfg(), providerOf(stubWeb3j(chainIdHex = "0x27d8"))).validate() }
    }

    @Test
    fun `mock mode is never checked`() {
        assertDoesNotThrow {
            OnchainRealModeValidator(cfg(mock = true, rpcUrl = "", registry = "", donorSecret = ""), providerOf(null)).validate()
        }
    }

    @Test
    fun `placeholder or blank secrets are refused without printing their value`() {
        assertThatThrownBy {
            OnchainRealModeValidator(cfg(donorSecret = "dev-placeholder-change-in-staging", assoSecret = ""), providerOf(null))
                .validate()
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("onchain.donor-address-secret")
            .hasMessageContaining("onchain.association-address-secret")
            .hasMessageNotContaining("dev-placeholder-change-in-staging")
    }

    @Test
    fun `non https RPC is refused except on loopback`() {
        assertThat(OnchainRealModeValidator.problems(cfg(rpcUrl = "http://rpc.chiadochain.net"))).isNotEmpty
        assertThat(OnchainRealModeValidator.problems(cfg(rpcUrl = "http://localhost:8545"))).isEmpty()
        assertThat(OnchainRealModeValidator.problems(cfg(rpcUrl = "http://172.30.10.204:8545"))).isNotEmpty
    }

    @Test
    fun `zero or blank registry and blank recorder key are refused`() {
        assertThat(OnchainRealModeValidator.problems(cfg(registry = "0x0000000000000000000000000000000000000000")))
            .anyMatch { it.contains("registry-address") }
        assertThat(OnchainRealModeValidator.problems(cfg(registry = ""))).anyMatch { it.contains("registry-address") }
        assertThat(OnchainRealModeValidator.problems(cfg(recorderPk = ""))).anyMatch { it.contains("recorder-pk") }
        assertThat(OnchainRealModeValidator.problems(cfg(pauserPk = ""))).anyMatch { it.contains("onchain.pauser-pk is blank") }
    }

    @Test
    fun `the local Anvil node accepts the committed dev placeholders`() {
        val local = cfg(
            chainId = OnchainRealModeValidator.ANVIL_CHAIN_ID,
            rpcUrl = "http://localhost:8545",
            donorSecret = "dev-placeholder-change-in-staging",
            assoSecret = "dev-placeholder-change-in-staging",
        )
        assertThat(OnchainRealModeValidator.problems(local)).isEmpty()
    }

    @Test
    fun `placeholders stay refused on a public chain even over loopback`() {
        val tunnel = cfg(rpcUrl = "http://localhost:8545", donorSecret = "dev-placeholder-change-in-staging")
        assertThat(OnchainRealModeValidator.problems(tunnel)).anyMatch { it.contains("donor-address-secret") }
    }

    @Test
    fun `the Anvil chain id over a remote RPC is refused`() {
        val remote = cfg(
            chainId = OnchainRealModeValidator.ANVIL_CHAIN_ID,
            rpcUrl = "https://rpc.example.org",
            donorSecret = "dev-placeholder-change-in-staging",
        )
        assertThat(OnchainRealModeValidator.problems(remote))
            .anyMatch { it.contains("onchain.chain-id") }
            .anyMatch { it.contains("donor-address-secret") }
    }

    @Test
    fun `the pauser must be a dedicated key distinct from the recorder`() {
        assertThat(OnchainRealModeValidator.problems(cfg(recorderPk = "0xabc", pauserPk = "0xABC")))
            .anyMatch { it.contains("onchain.pauser-pk must be a dedicated key") }
        assertThat(OnchainRealModeValidator.problems(cfg(recorderPk = "0xabc", pauserPk = "abc")))
            .anyMatch { it.contains("onchain.pauser-pk must be a dedicated key") }
    }

    @Test
    fun `problems never echo a key`() {
        val problems = OnchainRealModeValidator.problems(cfg(recorderPk = "0xabc", pauserPk = "0xabc"))
        assertThat(problems).noneMatch { it.contains("abc") }
    }
}

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
        donorSecret: String = "a-real-donor-secret",
        assoSecret: String = "a-real-association-secret",
    ) = OnchainConfig(
        rpcUrl = rpcUrl,
        chainId = 10200,
        registryAddress = registry,
        recorderPk = recorderPk,
        pollingIntervalMs = 1,
        receiptTimeoutMs = 1,
        donorAddressSecret = donorSecret,
        associationAddressSecret = assoSecret,
        mock = mock,
        worker = OnchainConfig.WorkerConfig(enabled = true, batchSize = 10, fixedDelayMs = 1),
    )

    @Test
    fun `a valid real configuration passes`() {
        assertDoesNotThrow { OnchainRealModeValidator(cfg()).validate() }
    }

    @Test
    fun `mock mode is never checked`() {
        assertDoesNotThrow {
            OnchainRealModeValidator(cfg(mock = true, rpcUrl = "", registry = "", donorSecret = "")).validate()
        }
    }

    @Test
    fun `placeholder or blank secrets are refused without printing their value`() {
        assertThatThrownBy {
            OnchainRealModeValidator(cfg(donorSecret = "dev-placeholder-change-in-staging", assoSecret = "")).validate()
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
    }
}

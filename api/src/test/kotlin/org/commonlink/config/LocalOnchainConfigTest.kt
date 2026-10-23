package org.commonlink.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource

/**
 * The local profile (`application.yml`) is simulated by default and, with `ONCHAIN_MOCK=false`, its defaults
 * target the local Anvil node deployed by `blockchain/script/deploy-registry.sh anvil` and pass
 * [OnchainRealModeValidator] without any environment variable.
 */
class LocalOnchainConfigTest {

    private val props: Map<String, Any?> by lazy {
        YamlPropertySourceLoader().load("base", ClassPathResource("application.yml"))
            .filterIsInstance<EnumerablePropertySource<*>>()
            .flatMap { source -> source.propertyNames.map { it to source.getProperty(it) } }
            .toMap()
    }

    /** Resolves `${VAR:default}` to its default, as Spring does when the variable is not set. */
    private fun default(key: String): String {
        val raw = props[key].toString()
        return Regex("""^\$\{[A-Z_]+:(.*)}$""").find(raw)?.groupValues?.get(1) ?: raw
    }

    @Test
    fun `local mode is simulated unless ONCHAIN_MOCK is set`() {
        assertThat(props["onchain.mock"]).isEqualTo("\${ONCHAIN_MOCK:true}")
    }

    @Test
    fun `local defaults target the Anvil deployment of deploy-registry sh`() {
        assertThat(default("onchain.chain-id")).isEqualTo(OnchainRealModeValidator.ANVIL_CHAIN_ID.toString())
        assertThat(default("onchain.rpc-url")).isEqualTo("http://localhost:8545")
        // First contract created by Anvil account 0 (nonce 0) — the address deploy-registry.sh anvil checks.
        assertThat(default("onchain.registry-address")).isEqualTo("0x5FbDB2315678afecb367f032d93F642f64180aa3")
    }

    @Test
    fun `local defaults pass the real-mode validator`() {
        val cfg = OnchainConfig(
            rpcUrl = default("onchain.rpc-url"),
            chainId = default("onchain.chain-id").toLong(),
            registryAddress = default("onchain.registry-address"),
            recorderPk = default("onchain.recorder-pk"),
            pollingIntervalMs = 1,
            receiptTimeoutMs = 1,
            donorAddressSecret = default("onchain.donor-address-secret"),
            associationAddressSecret = default("onchain.association-address-secret"),
            mock = false,
            worker = OnchainConfig.WorkerConfig(enabled = true, batchSize = 10, fixedDelayMs = 1),
        )
        assertThat(OnchainRealModeValidator.problems(cfg)).isEmpty()
    }
}

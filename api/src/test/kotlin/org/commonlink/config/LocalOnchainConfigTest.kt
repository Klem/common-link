package org.commonlink.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource

/**
 * The local profile (`application.yml`) runs the real client by default: its defaults target the local Anvil node
 * deployed by `blockchain/script/deploy-registry.sh anvil` and pass [OnchainRealModeValidator] without any
 * environment variable. The test profile stays simulated by default, so the regular build needs no chain.
 */
class LocalOnchainConfigTest {

    private fun load(path: String): Map<String, Any?> =
        YamlPropertySourceLoader().load(path, ClassPathResource(path))
            .filterIsInstance<EnumerablePropertySource<*>>()
            .flatMap { source -> source.propertyNames.map { it to source.getProperty(it) } }
            .toMap()

    private val props: Map<String, Any?> by lazy { load("application.yml") }

    /** Resolves `${VAR:default}` to its default, as Spring does when the variable is not set. */
    private fun default(key: String): String {
        val raw = props[key].toString()
        return Regex("""^\$\{[A-Z_]+:(.*)}$""").find(raw)?.groupValues?.get(1) ?: raw
    }

    @Test
    fun `local mode runs the real client unless ONCHAIN_MOCK is set`() {
        assertThat(props["onchain.mock"]).isEqualTo("\${ONCHAIN_MOCK:false}")
    }

    @Test
    fun `test profile is simulated unless ONCHAIN_TEST_MOCK is set`() {
        // The regular build (Clever Cloud: test -PnoDocker) has no chain; blockchain sprints opt in with
        // ONCHAIN_TEST_MOCK=false. Never ONCHAIN_MOCK: that app variable is also exposed to the Clever Cloud build.
        assertThat(load("application-test.yml")["onchain.mock"]).isEqualTo("\${ONCHAIN_TEST_MOCK:true}")
        // No registry / recorder override: in real mode the tests use the Anvil defaults of application.yml.
        assertThat(load("application-test.yml")).doesNotContainKeys("onchain.registry-address", "onchain.recorder-pk")
    }

    @Test
    fun `test profile simulates Bridge unless BRIDGE_TEST_DEMO_MODE is set`() {
        // Without credentials the regular build cannot start a context with demo-mode false; never BRIDGE_DEMO_MODE,
        // which Clever Cloud also exposes to the build.
        assertThat(load("application-test.yml")["app.bridge.demo-mode"]).isEqualTo("\${BRIDGE_TEST_DEMO_MODE:true}")
    }

    @Test
    fun `local defaults target the Anvil deployment of deploy-registry sh`() {
        assertThat(default("onchain.chain-id")).isEqualTo(OnchainRealModeValidator.ANVIL_CHAIN_ID.toString())
        assertThat(default("onchain.rpc-url")).isEqualTo("http://localhost:8545")
        // First contract created by Anvil account 0 (nonce 0) — the address deploy-registry.sh anvil checks.
        assertThat(default("onchain.registry-address")).isEqualTo("0x5FbDB2315678afecb367f032d93F642f64180aa3")
        // Anvil account 2 — the PAUSER_ADDR default of deploy-registry.sh anvil (dedicated key, decision 31).
        assertThat(org.web3j.crypto.Credentials.create(default("onchain.pauser-pk")).address)
            .isEqualToIgnoringCase("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC")
    }

    @Test
    fun `local defaults pass the real-mode validator`() {
        val cfg = OnchainConfig(
            rpcUrl = default("onchain.rpc-url"),
            chainId = default("onchain.chain-id").toLong(),
            registryAddress = default("onchain.registry-address"),
            recorderPk = default("onchain.recorder-pk"),
            pauserPk = default("onchain.pauser-pk"),
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

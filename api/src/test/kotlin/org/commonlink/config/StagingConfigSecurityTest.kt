package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource

class StagingConfigSecurityTest {

    private val effectiveProps: Map<String, Any?> by lazy {
        val loader = YamlPropertySourceLoader()
        val merged = mutableMapOf<String, Any?>()

        fun load(name: String, path: String) {
            loader.load(name, ClassPathResource(path))
                .filterIsInstance<EnumerablePropertySource<*>>()
                .forEach { source ->
                    source.propertyNames.forEach { key -> merged[key] = source.getProperty(key) }
                }
        }

        load("base", "application.yml")
        load("staging", "application-staging.yml")
        merged
    }

    private fun prop(key: String): Any? = effectiveProps[key]

    @Test
    fun `curator email is required with no empty default in staging`() {
        assertEquals("\${APP_CURATOR_EMAIL}", prop("app.curator.email"))
    }

    @Test
    fun `curator password is required with no empty default in staging`() {
        assertEquals("\${APP_CURATOR_PASSWORD}", prop("app.curator.password"))
    }

    @Test
    fun `compliance officer email is required with no empty default in staging`() {
        assertEquals("\${APP_COMPLIANCE_OFFICER_EMAIL}", prop("app.compliance-officer.email"))
    }

    @Test
    fun `compliance officer password is required with no empty default in staging`() {
        assertEquals("\${APP_COMPLIANCE_OFFICER_PASSWORD}", prop("app.compliance-officer.password"))
    }

    // ── On-chain (sprint B3.1): staging carries no default for the on-chain identity and secrets ──

    @Test
    fun `on-chain secrets and identity are required with no default in staging`() {
        assertEquals("\${DONOR_ADDRESS_SECRET}", prop("onchain.donor-address-secret"))
        assertEquals("\${ASSOCIATION_ADDRESS_SECRET}", prop("onchain.association-address-secret"))
        assertEquals("\${ONCHAIN_RECORDER_PK}", prop("onchain.recorder-pk"))
        assertEquals("\${ONCHAIN_RPC_URL}", prop("onchain.rpc-url"))
        assertEquals("\${ONCHAIN_CHAIN_ID}", prop("onchain.chain-id"))
        assertEquals("\${ONCHAIN_REGISTRY_ADDRESS}", prop("onchain.registry-address"))
    }

    @Test
    fun `staging explorer defaults to Chiado Blockscout`() {
        assertEquals("\${ONCHAIN_EXPLORER_BASE_URL:https://gnosis-chiado.blockscout.com}", prop("onchain.explorer.base-url"))
    }
}

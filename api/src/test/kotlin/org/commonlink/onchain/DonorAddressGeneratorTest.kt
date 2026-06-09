package org.commonlink.onchain

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.config.OnchainConfig
import org.junit.jupiter.api.Test
import java.util.UUID

class DonorAddressGeneratorTest {

    private val secret = "test-secret-key-32-bytes-minimum!"
    private val cfg = OnchainConfig(
        rpcUrl = "http://localhost:8545",
        chainId = 31337L,
        registryAddress = "0x0000000000000000000000000000000000000000",
        recorderPk = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
        curatorPk = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
        pollingIntervalMs = 1500L,
        receiptTimeoutMs = 60000L,
        donorAddressSecret = secret,
        worker = OnchainConfig.WorkerConfig(enabled = false, batchSize = 10, fixedDelayMs = 5000L),
    )
    private val generator = DonorAddressGenerator(cfg)

    @Test
    fun `generate returns stable EIP-55 checksummed address for same UUID`() {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val addr1 = generator.generate(uuid)
        val addr2 = generator.generate(uuid)
        assertThat(addr1).isEqualTo(addr2)
        assertThat(addr1).matches("0x[0-9a-fA-F]{40}")
        // EIP-55: address should have mixed case (checksum)
        assertThat(addr1).isNotEqualTo(addr1.lowercase())
    }

    @Test
    fun `generate returns distinct addresses for distinct UUIDs`() {
        val addresses = (1..10_000).map { UUID.randomUUID() }.map { generator.generate(it) }.toSet()
        assertThat(addresses).hasSize(10_000)
    }
}

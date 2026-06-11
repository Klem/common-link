package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnchainConfigTest {

    private val cfg = OnchainConfig(
        rpcUrl = "http://localhost:8545",
        chainId = 31337L,
        registryAddress = "0xRegistry",
        recorderPk = "0xDEADBEEFrecorderPrivKey",
        curatorPk = "0xDEADBEEFcuratorPrivKey",
        pollingIntervalMs = 1500L,
        receiptTimeoutMs = 60000L,
        donorAddressSecret = "super-secret-hmac-key",
        worker = OnchainConfig.WorkerConfig(enabled = false, batchSize = 10, fixedDelayMs = 5000L)
    )

    @Test
    fun `toString does not contain recorderPk value`() {
        assertFalse(cfg.toString().contains("0xDEADBEEFrecorderPrivKey"))
    }

    @Test
    fun `toString does not contain curatorPk value`() {
        assertFalse(cfg.toString().contains("0xDEADBEEFcuratorPrivKey"))
    }

    @Test
    fun `toString does not contain donorAddressSecret value`() {
        assertFalse(cfg.toString().contains("super-secret-hmac-key"))
    }

    @Test
    fun `toString contains redacted placeholder for all three secrets`() {
        val s = cfg.toString()
        assertTrue(s.contains("recorderPk=***"))
        assertTrue(s.contains("curatorPk=***"))
        assertTrue(s.contains("donorAddressSecret=***"))
    }
}

package org.commonlink.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment

class OnchainMockGuardTest {

    private fun buildConfig(mock: Boolean) = OnchainConfig(
        rpcUrl = "http://localhost:8545",
        chainId = 31337L,
        registryAddress = "0x0000000000000000000000000000000000000000",
        recorderPk = "0x0000000000000000000000000000000000000000000000000000000000000001",
        curatorPk = "0x0000000000000000000000000000000000000000000000000000000000000001",
        pollingIntervalMs = 1500L,
        receiptTimeoutMs = 60000L,
        donorAddressSecret = "test-secret",
        mock = mock,
        worker = OnchainConfig.WorkerConfig(enabled = false, batchSize = 10, fixedDelayMs = 5000L),
    )

    private fun guard(profiles: Array<String>, mock: Boolean): OnchainMockGuard {
        val env = mockk<Environment>()
        every { env.activeProfiles } returns profiles
        return OnchainMockGuard(env, buildConfig(mock))
    }

    @Test
    fun `throws when prod active and mock true`() {
        assertThrows(IllegalStateException::class.java) {
            guard(arrayOf("prod"), true).validate()
        }
    }

    @Test
    fun `no exception when prod active and mock false`() {
        assertDoesNotThrow { guard(arrayOf("prod"), false).validate() }
    }

    @Test
    fun `no exception when staging active and mock true`() {
        assertDoesNotThrow { guard(arrayOf("staging"), true).validate() }
    }

    @Test
    fun `no exception when no profile and mock true`() {
        assertDoesNotThrow { guard(emptyArray(), true).validate() }
    }
}

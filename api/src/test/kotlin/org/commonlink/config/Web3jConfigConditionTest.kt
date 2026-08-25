package org.commonlink.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j

/**
 * Guards the `onchain.mock=true` boot path: [Web3jConfig] must stay out of the context, otherwise
 * the credential beans are built from the empty `recorder-pk` / `curator-pk` prod defaults and
 * startup dies with `Zero length BigInteger`.
 */
class Web3jConfigConditionTest {

    private val runner = ApplicationContextRunner()
        .withBean(
            OnchainConfig::class.java,
            {
                OnchainConfig(
                    rpcUrl = "http://localhost:8545",
                    chainId = 31337L,
                    registryAddress = "0x0000000000000000000000000000000000000000",
                    recorderPk = "0x0000000000000000000000000000000000000000000000000000000000000001",
                    curatorPk = "0x0000000000000000000000000000000000000000000000000000000000000002",
                    pollingIntervalMs = 1500L,
                    receiptTimeoutMs = 60000L,
                    donorAddressSecret = "test-secret",
                    associationAddressSecret = "test-secret",
                    worker = OnchainConfig.WorkerConfig(enabled = false, batchSize = 10, fixedDelayMs = 5000L),
                )
            },
        )
        .withUserConfiguration(Web3jConfig::class.java)

    @Test
    fun `web3j beans are absent when mock is true`() {
        runner.withPropertyValues("onchain.mock=true").run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx).doesNotHaveBean(Web3jConfig::class.java)
            assertThat(ctx).doesNotHaveBean(Web3j::class.java)
            assertThat(ctx).doesNotHaveBean("recorderCredentials")
            assertThat(ctx).doesNotHaveBean("curatorCredentials")
        }
    }

    @Test
    fun `web3j config is active when mock is false`() {
        runner.withPropertyValues("onchain.mock=false").run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx).hasSingleBean(Web3jConfig::class.java)
            assertThat(ctx).hasBean("recorderCredentials")
            assertThat(ctx).hasBean("curatorCredentials")
        }
    }

    @Test
    fun `web3j config is active when mock is absent`() {
        runner.run { ctx -> assertThat(ctx).hasSingleBean(Web3jConfig::class.java) }
    }

    @Test
    fun `scheduling stays enabled when mock is true`() {
        ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig::class.java)
            .withPropertyValues("onchain.mock=true")
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).hasSingleBean(ScheduledAnnotationBeanPostProcessor::class.java)
            }
    }

    @Test
    fun `empty recorder key would break credential creation`() {
        // Pins the failure mode the condition above exists to prevent.
        val ex = org.junit.jupiter.api.assertThrows<NumberFormatException> { Credentials.create("") }
        assertThat(ex).hasMessageContaining("Zero length BigInteger")
    }
}

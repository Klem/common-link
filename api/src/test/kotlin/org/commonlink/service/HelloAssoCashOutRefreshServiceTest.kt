package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.HelloAssoCashOutProperties
import org.commonlink.config.PaymentsProperties
import org.commonlink.entity.HelloAssoCashOutSync
import org.commonlink.repository.HelloAssoCashOutSyncRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/** On-demand refresh limits (mockk) and the scheduler's cadence wiring. The cross-instance guard is proven on PostgreSQL. */
class HelloAssoCashOutRefreshServiceTest {

    private val syncRepo = mockk<HelloAssoCashOutSyncRepository>()
    private val executor = mockk<HelloAssoCashOutSyncExecutor>()
    private val now = Instant.parse("2026-11-10T08:00:00Z")
    private val associationId = UUID.randomUUID()
    private val known = CashOutSyncResult(Instant.parse("2026-11-10T07:55:00Z"), null)

    private fun service(timeout: Duration = Duration.ofSeconds(10)) = HelloAssoCashOutRefreshService(
        syncRepo, executor, PaymentsProperties(helloasso = HelloAssoCashOutProperties(cashoutRefreshTimeout = timeout)),
        TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
    ).also { it.clock = Clock.fixed(now, ZoneOffset.UTC) }

    init {
        every { syncRepo.insertIfMissing(associationId) } returns 0
        every { executor.resultOf(associationId) } returns known
    }

    @Test
    fun `a request within the interval runs no pass and tells when the next one is possible`() {
        every { syncRepo.claimRefresh(associationId, now, now.minus(Duration.ofMinutes(15))) } returns 0
        every { syncRepo.findById(associationId) } returns Optional.of(
            HelloAssoCashOutSync(associationId, lastRefreshRequestedAt = Instant.parse("2026-11-10T07:55:00Z")),
        )

        val outcome = service().requestRefresh(associationId)

        assertFalse(outcome.refreshed)
        assertEquals(known, outcome.result)
        assertEquals(Instant.parse("2026-11-10T08:10:00Z"), outcome.nextRefreshAvailableAt)
        verify(exactly = 0) { executor.syncAssociation(any()) }
    }

    @Test
    fun `a request past the interval runs the pass and returns its result`() {
        val fresh = CashOutSyncResult(now, null)
        every { syncRepo.claimRefresh(associationId, now, now.minus(Duration.ofMinutes(15))) } returns 1
        every { executor.syncAssociation(associationId) } returns fresh

        val outcome = service().requestRefresh(associationId)

        assertTrue(outcome.refreshed)
        assertEquals(fresh, outcome.result)
        assertEquals(Instant.parse("2026-11-10T08:15:00Z"), outcome.nextRefreshAvailableAt)
    }

    @Test
    fun `a pass slower than the timeout answers with the known state and goes on`() {
        every { syncRepo.claimRefresh(associationId, now, any()) } returns 1
        every { executor.syncAssociation(associationId) } answers { Thread.sleep(500); CashOutSyncResult(now, null) }

        val outcome = service(timeout = Duration.ofMillis(50)).requestRefresh(associationId)

        assertFalse(outcome.refreshed)
        assertEquals(known, outcome.result)
        verify(timeout = 2000, exactly = 1) { executor.syncAssociation(associationId) }
    }

    @Test
    fun `the scheduler reads the configured initial delay and fixed delay`() {
        val tick = HelloAssoCashOutSyncScheduler::class.java.getMethod("tick").getAnnotation(Scheduled::class.java)
        assertEquals("\${app.payments.helloasso.cashout-sync-initial-delay-ms:300000}", tick.initialDelayString)
        assertEquals("\${app.payments.helloasso.cashout-sync-delay-ms:86400000}", tick.fixedDelayString)

        val condition = HelloAssoCashOutSyncScheduler::class.java.getAnnotation(ConditionalOnProperty::class.java)
        assertEquals("app.payments.helloasso", condition.prefix)
        assertEquals(listOf("cashout-sync-enabled"), condition.name.toList())
    }

    @EnableScheduling
    @Import(HelloAssoCashOutSyncScheduler::class)
    private class SchedulerConfig

    @Test
    fun `the scheduler starts in a Spring context and runs its first pass after the initial delay`() {
        every { executor.syncAll() } returns Unit
        ApplicationContextRunner()
            .withUserConfiguration(SchedulerConfig::class.java)
            .withBean(HelloAssoCashOutSyncExecutor::class.java, { executor })
            .withPropertyValues("app.payments.helloasso.cashout-sync-initial-delay-ms=10")
            .run { context ->
                assertEquals(1, context.getBeansOfType(HelloAssoCashOutSyncScheduler::class.java).size)
                verify(timeout = 5000, atLeast = 1) { executor.syncAll() }
            }
    }

    @Test
    fun `the scheduler is not created when disabled`() {
        ApplicationContextRunner()
            .withUserConfiguration(SchedulerConfig::class.java)
            .withBean(HelloAssoCashOutSyncExecutor::class.java, { executor })
            .withPropertyValues("app.payments.helloasso.cashout-sync-enabled=false")
            .run { context -> assertTrue(context.getBeansOfType(HelloAssoCashOutSyncScheduler::class.java).isEmpty()) }
    }
}

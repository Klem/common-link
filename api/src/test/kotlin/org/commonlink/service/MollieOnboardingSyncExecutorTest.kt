package org.commonlink.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.MollieConnectConfig
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieOnboardingSyncCandidate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.ResourceAccessException
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [MollieOnboardingSyncExecutor] — the scheduled onboarding status sweep.
 *
 * Pins the sprint 20 / S2 guarantees whose regression would be silent: a connection that collects
 * is never polled (Q1), the cadence boundaries (5 min / 1 h dormant), mock rows never reach Mollie
 * and never warn, one failing association does not stop the others, and
 * `disable-onboarding-sync` is the only switch.
 */
class MollieOnboardingSyncExecutorTest {

    private val connectionRepo: MollieConnectionRepository = mockk()
    private val service: MollieConnectService = mockk()

    private fun config(disabled: Boolean = false) = MollieConnectConfig(
        clientId = "app_test",
        clientSecret = "secret",
        redirectUri = "https://api.test/callback",
        scopes = "onboarding.read",
        disableOnboardingSync = disabled,
    )

    private val executor = MollieOnboardingSyncExecutor(connectionRepo, service, config())

    private val logAppender = ListAppender<ILoggingEvent>()
    private val executorLogger = LoggerFactory.getLogger(MollieOnboardingSyncExecutor::class.java) as Logger

    @BeforeEach
    fun attachLogs() {
        logAppender.start()
        executorLogger.addAppender(logAppender)
        every { service.syncOnboardingStatus(any()) } returns null
    }

    @AfterEach
    fun detachLogs() {
        executorLogger.detachAppender(logAppender)
    }

    private fun candidate(
        status: MollieOnboardingStatus,
        canReceivePayments: Boolean = false,
        syncedAgo: Duration?,
        statusSinceAgo: Duration = Duration.ofDays(1),
        refreshToken: String = "refresh_real_token",
        associationId: UUID = UUID.randomUUID(),
    ) = MollieOnboardingSyncCandidate(
        associationId = associationId,
        refreshToken = refreshToken,
        onboardingStatus = status,
        canReceivePayments = canReceivePayments,
        onboardingStatusSince = Instant.now().minus(statusSinceAgo),
        lastSyncedAt = syncedAgo?.let { Instant.now().minus(it) },
    )

    private fun sweepOver(vararg candidates: MollieOnboardingSyncCandidate) {
        every { connectionRepo.findActiveNotCollecting() } returns candidates.toList()
        executor.sweep()
    }

    @Test
    fun `a connection that collects is never polled, however stale`() {
        val collecting = candidate(MollieOnboardingStatus.COMPLETED, canReceivePayments = true, syncedAgo = Duration.ofDays(30))

        sweepOver(collecting)

        verify(exactly = 0) { service.syncOnboardingStatus(any()) }
    }

    @Test
    fun `COMPLETED without payments synced 6 min ago is polled`() {
        val c = candidate(MollieOnboardingStatus.COMPLETED, canReceivePayments = false, syncedAgo = Duration.ofMinutes(6))

        sweepOver(c)

        verify(exactly = 1) { service.syncOnboardingStatus(c.associationId) }
    }

    @Test
    fun `NEEDS_DATA for 8 days is dormant - not polled after 30 min, polled after 61 min`() {
        val recent = candidate(
            MollieOnboardingStatus.NEEDS_DATA, syncedAgo = Duration.ofMinutes(30), statusSinceAgo = Duration.ofDays(8),
        )
        val old = candidate(
            MollieOnboardingStatus.NEEDS_DATA, syncedAgo = Duration.ofMinutes(61), statusSinceAgo = Duration.ofDays(8),
        )

        sweepOver(recent, old)

        verify(exactly = 0) { service.syncOnboardingStatus(recent.associationId) }
        verify(exactly = 1) { service.syncOnboardingStatus(old.associationId) }
    }

    @Test
    fun `NEEDS_DATA for less than 7 days keeps the 5-minute cadence`() {
        val due = candidate(MollieOnboardingStatus.NEEDS_DATA, syncedAgo = Duration.ofMinutes(6), statusSinceAgo = Duration.ofDays(2))
        val notDue = candidate(MollieOnboardingStatus.NEEDS_DATA, syncedAgo = Duration.ofMinutes(4), statusSinceAgo = Duration.ofDays(2))

        sweepOver(due, notDue)

        verify(exactly = 1) { service.syncOnboardingStatus(due.associationId) }
        verify(exactly = 0) { service.syncOnboardingStatus(notDue.associationId) }
    }

    @Test
    fun `IN_REVIEW synced 6 min ago is polled, a never-synced connection too`() {
        val inReview = candidate(MollieOnboardingStatus.IN_REVIEW, syncedAgo = Duration.ofMinutes(6))
        val neverSynced = candidate(MollieOnboardingStatus.NEEDS_DATA, syncedAgo = null)

        sweepOver(inReview, neverSynced)

        verify(exactly = 1) { service.syncOnboardingStatus(inReview.associationId) }
        verify(exactly = 1) { service.syncOnboardingStatus(neverSynced.associationId) }
    }

    @Test
    fun `IN_REVIEW for weeks is never treated as dormant`() {
        val c = candidate(MollieOnboardingStatus.IN_REVIEW, syncedAgo = Duration.ofMinutes(6), statusSinceAgo = Duration.ofDays(30))

        sweepOver(c)

        verify(exactly = 1) { service.syncOnboardingStatus(c.associationId) }
    }

    @Test
    fun `mock connection is never polled and never warns`() {
        val mock = candidate(MollieOnboardingStatus.NEEDS_DATA, syncedAgo = Duration.ofDays(1), refreshToken = MOCK_TOKEN_SENTINEL)

        sweepOver(mock)

        verify(exactly = 0) { service.syncOnboardingStatus(any()) }
        assertTrue(logAppender.list.none { it.level.isGreaterOrEqual(Level.WARN) })
    }

    @Test
    fun `one network failure does not stop the other connections`() {
        val first = candidate(MollieOnboardingStatus.NEEDS_DATA, syncedAgo = Duration.ofMinutes(10))
        val failing = candidate(MollieOnboardingStatus.IN_REVIEW, syncedAgo = Duration.ofMinutes(10))
        val third = candidate(MollieOnboardingStatus.COMPLETED, syncedAgo = Duration.ofMinutes(10))
        every { service.syncOnboardingStatus(failing.associationId) } throws ResourceAccessException("connect timed out")

        sweepOver(first, failing, third)

        verify(exactly = 1) { service.syncOnboardingStatus(first.associationId) }
        verify(exactly = 1) { service.syncOnboardingStatus(failing.associationId) }
        verify(exactly = 1) { service.syncOnboardingStatus(third.associationId) }
        assertEquals(1, logAppender.list.count { it.level == Level.WARN })
        assertEquals(1, logAppender.list.count { it.level == Level.INFO })
    }

    @Test
    fun `disable-onboarding-sync is the single switch - nothing is queried nor polled`() {
        val disabled = MollieOnboardingSyncExecutor(connectionRepo, service, config(disabled = true))

        disabled.sweep()

        verify(exactly = 0) { connectionRepo.findActiveNotCollecting() }
        verify(exactly = 0) { service.syncOnboardingStatus(any()) }
    }

    @Test
    fun `cadence function returns the configured intervals`() {
        val now = Instant.now()
        assertEquals(3_600, executor.recheckIntervalSeconds(MollieOnboardingStatus.NEEDS_DATA, now.minus(Duration.ofDays(8)), now))
        assertEquals(300, executor.recheckIntervalSeconds(MollieOnboardingStatus.NEEDS_DATA, now.minus(Duration.ofDays(6)), now))
        assertEquals(300, executor.recheckIntervalSeconds(MollieOnboardingStatus.IN_REVIEW, now.minus(Duration.ofDays(8)), now))
        assertEquals(300, executor.recheckIntervalSeconds(MollieOnboardingStatus.COMPLETED, now.minus(Duration.ofDays(8)), now))
    }

    @Test
    fun `scheduler interval resolves to the configured property in every profile`() {
        val scheduled = MollieOnboardingSyncScheduler::class.java.getMethod("tick").getAnnotation(Scheduled::class.java)
        assertEquals("\${app.mollie.connect.onboarding-sync.fixed-delay-ms:60000}", scheduled.fixedDelayString)
        assertEquals("\${app.mollie.connect.onboarding-sync.initial-delay-ms:120000}", scheduled.initialDelayString)

        for (file in listOf("application.yml", "application-staging.yml", "application-prod.yml")) {
            val props = YamlPropertiesFactoryBean().apply { setResources(ClassPathResource(file)) }.getObject()!!
            val prefix = "app.mollie.connect.onboarding-sync."
            assertEquals("60000", props.getProperty(prefix + "fixed-delay-ms"), file)
            assertEquals("300", props.getProperty(prefix + "pending-recheck-seconds"), file)
            assertEquals("7", props.getProperty(prefix + "dormant-after-days"), file)
            assertEquals("3600", props.getProperty(prefix + "dormant-recheck-seconds"), file)
            assertEquals("30", props.getProperty(prefix + "forced-refresh-min-interval-seconds"), file)
        }
    }
}

package org.commonlink.service

import org.commonlink.exception.HelloAssoRateLimitedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** The three HelloAsso auth windows (10 / 10 s, 20 / 10 min, 50 / h) on a controllable clock. */
class HelloAssoAuthRateLimiterTest {

    private class MovableClock(var now: Instant = Instant.parse("2026-11-02T10:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(d: Duration) { now = now.plus(d) }
    }

    private val clock = MovableClock()
    private val limiter = HelloAssoAuthRateLimiter(clock)

    private fun burst(n: Int) = repeat(n) { limiter.acquire("test") }

    @Test
    fun `10 calls per 10 seconds, then free again after the window`() {
        burst(10)
        assertThrows<HelloAssoRateLimitedException> { limiter.acquire("test") }

        clock.advance(Duration.ofSeconds(11))
        assertDoesNotThrow { limiter.acquire("test") }
    }

    @Test
    fun `20 calls per 10 minutes even when spread over 10-second windows`() {
        burst(10)
        clock.advance(Duration.ofSeconds(11))
        burst(10)
        clock.advance(Duration.ofSeconds(11))

        assertThrows<HelloAssoRateLimitedException> { limiter.acquire("test") }
        clock.advance(Duration.ofMinutes(10))
        assertDoesNotThrow { limiter.acquire("test") }
    }

    @Test
    fun `50 calls per hour even when spread over 10-minute windows`() {
        repeat(5) {
            burst(10)
            clock.advance(Duration.ofMinutes(11))
        }

        assertThrows<HelloAssoRateLimitedException> { limiter.acquire("test") }
        clock.advance(Duration.ofMinutes(10))
        assertDoesNotThrow { limiter.acquire("test") }
    }
}

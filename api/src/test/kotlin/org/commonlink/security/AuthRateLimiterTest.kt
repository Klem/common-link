package org.commonlink.security

import org.commonlink.exception.RateLimitException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthRateLimiterTest {

    private val limiter = AuthRateLimiter()

    @Test
    fun `allows up to maxAttempts, then refuses the next one`() {
        val key = "test:${System.nanoTime()}"
        repeat(3) { limiter.check(key, maxAttempts = 3, windowMinutes = 10) }

        assertThrows<RateLimitException> { limiter.check(key, maxAttempts = 3, windowMinutes = 10) }
    }

    @Test
    fun `different keys have independent windows`() {
        val keyA = "test:a:${System.nanoTime()}"
        val keyB = "test:b:${System.nanoTime()}"
        limiter.check(keyA, maxAttempts = 1, windowMinutes = 10)

        // keyB's own first attempt must not be refused by keyA's exhausted window.
        limiter.check(keyB, maxAttempts = 1, windowMinutes = 10)
    }

    @Test
    fun `RateLimitException retryAfterSeconds is windowMinutes times 60, not the exception's own default`() {
        val key = "test:${System.nanoTime()}"
        limiter.check(key, maxAttempts = 1, windowMinutes = 1)

        val ex = assertThrows<RateLimitException> { limiter.check(key, maxAttempts = 1, windowMinutes = 1) }

        assertEquals(60L, ex.retryAfterSeconds)
    }

    @Test
    fun `RateLimitException retryAfterSeconds scales with a ten-minute window`() {
        val key = "test:${System.nanoTime()}"
        limiter.check(key, maxAttempts = 1, windowMinutes = 10)

        val ex = assertThrows<RateLimitException> { limiter.check(key, maxAttempts = 1, windowMinutes = 10) }

        assertEquals(600L, ex.retryAfterSeconds)
    }
}

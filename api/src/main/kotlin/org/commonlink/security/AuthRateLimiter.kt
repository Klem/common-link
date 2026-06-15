package org.commonlink.security

import org.commonlink.exception.RateLimitException
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory sliding-window rate limiter for authentication endpoints.
 *
 * Each key tracks a list of attempt timestamps. On every call, attempts outside the window are
 * pruned; if the remaining count reaches [maxAttempts], [RateLimitException] is thrown.
 *
 * Keys should encode the endpoint and the identity being throttled, e.g.:
 *   "login:email:user@example.com"
 *   "login:ip:203.0.113.1"
 *   "google:ip:203.0.113.1"
 *   "refresh:ip:203.0.113.1"
 */
@Component
class AuthRateLimiter {

    private val windows = ConcurrentHashMap<String, MutableList<Instant>>()

    fun check(key: String, maxAttempts: Int = 5, windowMinutes: Long = 10) {
        val now = Instant.now()
        val cutoff = now.minus(windowMinutes, ChronoUnit.MINUTES)
        val attempts = windows.computeIfAbsent(key) { Collections.synchronizedList(mutableListOf()) }
        synchronized(attempts) {
            attempts.removeIf { it.isBefore(cutoff) }
            if (attempts.size >= maxAttempts) throw RateLimitException()
            attempts.add(now)
        }
    }
}

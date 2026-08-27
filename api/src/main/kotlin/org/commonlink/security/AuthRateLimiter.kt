package org.commonlink.security

import org.commonlink.exception.RateLimitException
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory sliding-window rate limiter for authentication and other abuse-prone endpoints.
 *
 * Each key tracks a list of attempt timestamps. On every call, attempts outside the window are
 * pruned; if the remaining count reaches [maxAttempts], [RateLimitException] is thrown.
 *
 * Keys should encode the endpoint and the identity being throttled, e.g.:
 *   "login:email:user@example.com"
 *   "login:ip:203.0.113.1"
 *   "google:ip:203.0.113.1"
 *   "refresh:ip:203.0.113.1"
 *   "donation:widget:clk_xxx"
 *
 * IP-keyed callers must derive the address through [ClientIpResolver]: keying on a raw
 * `X-Forwarded-For` value makes every quota bypassable in one header
 * (security audit 2026-08-20, M2).
 *
 * **Known limitation** — state is per JVM. Running more than one instance multiplies every quota by
 * the instance count, and a restart clears them. Adequate for a single-instance deployment; a
 * shared store is required before scaling out.
 */
@Component
class AuthRateLimiter {

    private val windows = ConcurrentHashMap<String, MutableList<Instant>>()

    /** Calls since the last sweep of stale keys. See [sweepIfDue]. */
    private val callsSinceSweep = AtomicInteger(0)

    /**
     * Records an attempt against [key] and refuses it when the window is already full.
     *
     * @param key Identity being throttled, endpoint-prefixed.
     * @param maxAttempts Attempts allowed within the window, inclusive of the current one.
     * @param windowMinutes Width of the sliding window.
     * @throws RateLimitException when [maxAttempts] has already been reached.
     */
    fun check(key: String, maxAttempts: Int = 5, windowMinutes: Long = 10) {
        val now = Instant.now()
        val cutoff = now.minus(windowMinutes, ChronoUnit.MINUTES)
        val attempts = windows.computeIfAbsent(key) { Collections.synchronizedList(mutableListOf()) }
        synchronized(attempts) {
            attempts.removeIf { it.isBefore(cutoff) }
            if (attempts.size >= maxAttempts) throw RateLimitException(retryAfterSeconds = windowMinutes * 60)
            attempts.add(now)
        }
        sweepIfDue(cutoff)
    }

    /**
     * Drops keys whose every attempt has aged out of [cutoff].
     *
     * Without this the map only ever grows: one key per distinct identity seen since startup, and
     * distinct identities are cheap to manufacture (one per e-mail tried, one per forwarded address).
     * Amortised over [SWEEP_EVERY] calls so the common path stays a single map lookup.
     */
    private fun sweepIfDue(cutoff: Instant) {
        if (callsSinceSweep.incrementAndGet() < SWEEP_EVERY) return
        callsSinceSweep.set(0)
        windows.entries.removeIf { (_, attempts) ->
            synchronized(attempts) { attempts.all { it.isBefore(cutoff) } }
        }
    }

    private companion object {
        const val SWEEP_EVERY = 500
    }
}

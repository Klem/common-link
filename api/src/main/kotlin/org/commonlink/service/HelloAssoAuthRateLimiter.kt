package org.commonlink.service

import org.commonlink.exception.HelloAssoRateLimitedException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Local guard of HelloAsso's authentication API limit: **10 calls / 10 s, 20 / 10 min, 50 / h**, beyond which
 * HelloAsso answers 429.
 *
 * Whether HelloAsso counts per API key or per source IP is not documented, so the most cautious reading applies:
 * one **global** budget for every token call of this instance. Known limitation: state is per JVM (as
 * [org.commonlink.security.AuthRateLimiter]); several instances would each get the full budget.
 */
@Component
class HelloAssoAuthRateLimiter internal constructor(private val clock: Clock) {

    /** Spring constructor: wall clock. */
    constructor() : this(Clock.systemUTC())

    private val windows = listOf(
        Duration.ofSeconds(10) to 10,
        Duration.ofMinutes(10) to 20,
        Duration.ofHours(1) to 50,
    )
    private val longest = windows.maxOf { it.first }
    private val calls = ArrayDeque<Instant>()

    /**
     * Records one token call, or refuses it without recording when any window is already full.
     *
     * @throws HelloAssoRateLimitedException when the call would exceed a window — no HTTP call must then be made.
     */
    @Synchronized
    fun acquire(operation: String) {
        val now = clock.instant()
        while (calls.isNotEmpty() && !calls.first().isAfter(now.minus(longest))) calls.removeFirst()
        for ((width, max) in windows) {
            val cutoff = now.minus(width)
            if (calls.count { it.isAfter(cutoff) } >= max) throw HelloAssoRateLimitedException(operation)
        }
        calls.addLast(now)
    }
}

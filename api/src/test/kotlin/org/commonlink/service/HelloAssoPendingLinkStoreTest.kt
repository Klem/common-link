package org.commonlink.service

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Single use, owner-bound and 10-minute TTL of verified HelloAsso links. */
class HelloAssoPendingLinkStoreTest {

    private class MovableClock(var now: Instant = Instant.parse("2026-11-02T10:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MovableClock()
    private val store = HelloAssoPendingLinkStore(clock)
    private val associationId = UUID.randomUUID()

    private fun put() = store.put(
        HelloAssoPendingLink(associationId, "client", "slug", "Org", true, HelloAssoTokens("a", "r", 1800), clock.now)
    )

    @Test
    fun `a verifyId is usable once`() {
        val id = put()
        assertNotNull(store.take(id, associationId))
        assertNull(store.take(id, associationId))
    }

    @Test
    fun `another association cannot use it, and it stays available to its owner`() {
        val id = put()
        assertNull(store.take(id, UUID.randomUUID()))
        assertNotNull(store.take(id, associationId))
    }

    @Test
    fun `it expires after 10 minutes`() {
        val id = put()
        clock.now = clock.now.plus(Duration.ofMinutes(10)).plusSeconds(1)
        assertNull(store.take(id, associationId))
    }
}

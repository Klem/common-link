package org.commonlink.service

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A HelloAsso link verified by the key check, waiting for the association's mandate. Holds tokens, never the secret;
 * [toString] never prints the tokens.
 */
data class HelloAssoPendingLink(
    val associationId: UUID,
    val clientId: String,
    val slug: String,
    val orgName: String,
    val canCollect: Boolean,
    val tokens: HelloAssoTokens,
    val verifiedAt: Instant,
) {
    override fun toString(): String = "HelloAssoPendingLink(associationId=$associationId, slug=$slug)"
}

/**
 * In-memory, single-use store of verified HelloAsso links, valid [TTL] — nothing about an unconfirmed link is ever
 * persisted.
 *
 * Known limitation: per JVM. With several instances, or after a restart, a `verifyId` may not be found and the
 * association simply verifies its keys again.
 */
@Component
class HelloAssoPendingLinkStore internal constructor(private val clock: Clock) {

    /** Spring constructor: wall clock. */
    constructor() : this(Clock.systemUTC())

    companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }

    private val links = ConcurrentHashMap<UUID, HelloAssoPendingLink>()

    /** Keeps [link] for [TTL] and returns its one-time `verifyId`. Expired entries are dropped on the way. */
    fun put(link: HelloAssoPendingLink): UUID {
        val now = clock.instant()
        links.entries.removeIf { it.value.verifiedAt.plus(TTL).isBefore(now) }
        val verifyId = UUID.randomUUID()
        links[verifyId] = link
        return verifyId
    }

    /**
     * Removes and returns the link of [verifyId] if it exists, is not expired and belongs to [associationId]. A
     * `verifyId` is consumed by its first valid use.
     */
    fun take(verifyId: UUID, associationId: UUID): HelloAssoPendingLink? {
        val link = links[verifyId] ?: return null
        if (link.associationId != associationId) return null
        if (!links.remove(verifyId, link)) return null
        return link.takeIf { !it.verifiedAt.plus(TTL).isBefore(clock.instant()) }
    }
}

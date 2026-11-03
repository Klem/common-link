package org.commonlink.service

import org.commonlink.config.HelloAssoProperties
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.event.HelloAssoConnectionBrokenEvent
import org.commonlink.exception.HelloAssoException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.repository.HelloAssoConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Marks a HelloAsso link BROKEN once HelloAsso rejected its refresh token, and announces it once
 * ([HelloAssoConnectionBrokenEvent], emailed after commit). A separate bean so the caller's failed transaction (which
 * holds no lock any more) never rolls the state back.
 */
@Component
class HelloAssoConnectionBreaker(
    private val connectionRepository: HelloAssoConnectionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** ACTIVE → BROKEN (cannot collect). No-op, and no event, when the link is not ACTIVE any more. */
    @Transactional
    fun markBroken(associationId: UUID) {
        val connection = connectionRepository.findByAssociationIdForUpdate(associationId) ?: return
        if (connection.state != HelloAssoConnectionState.ACTIVE) return
        connection.state = HelloAssoConnectionState.BROKEN
        connection.canCollect = false
        connection.updatedAt = Instant.now()
        connectionRepository.save(connection)
        log.warn("HelloAsso link of association {} is BROKEN: refresh token rejected", associationId)
        eventPublisher.publishEvent(HelloAssoConnectionBrokenEvent(associationId))
    }
}

/**
 * Scheduled renewal of HelloAsso token pairs. A refresh token lives 30 days and is rotated on every renewal, so each
 * ACTIVE link of a HELLOASSO association is renewed once its last renewal is older than
 * [HelloAssoProperties.tokenRefreshInterval] (weekly), keeping the chain alive even without donations.
 *
 * Each renewal goes through [HelloAssoTokenManager.refreshIfOlderThan] (row lock + re-check): two instances sweeping at
 * once never renew the same link twice. Never throws.
 */
@Service
class HelloAssoTokenRefreshExecutor(
    private val connectionRepository: HelloAssoConnectionRepository,
    private val tokenManager: HelloAssoTokenManager,
    private val breaker: HelloAssoConnectionBreaker,
    private val properties: HelloAssoProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Renews every due link; exits at once, without any further query, when no HELLOASSO association has an ACTIVE link. */
    fun sweep() {
        if (!connectionRepository.existsActiveForHelloAssoAssociations()) {
            log.debug("HelloAsso token refresh: no active HelloAsso link")
            return
        }
        val threshold = Instant.now().minus(properties.tokenRefreshInterval)
        val due = connectionRepository.findActiveHelloAssoRefreshCandidates(threshold)
        var renewed = 0
        var broken = 0
        var deferred = 0
        for (associationId in due) {
            try {
                if (tokenManager.refreshIfOlderThan(associationId, threshold)) renewed++
            } catch (ex: HelloAssoUnauthorizedException) {
                breaker.markBroken(associationId)
                broken++
            } catch (ex: HelloAssoException) {
                log.warn("HelloAsso token refresh deferred for association {}: {}", associationId, ex.javaClass.simpleName)
                deferred++
            } catch (ex: IllegalStateException) {
                log.debug("HelloAsso token refresh skipped for association {}: {}", associationId, ex.message)
            }
        }
        if (due.isNotEmpty()) log.info("HelloAsso token refresh: {} renewed, {} broken, {} deferred", renewed, broken, deferred)
    }
}

/** Thin scheduler of [HelloAssoTokenRefreshExecutor] (hourly check; each link is renewed weekly). */
@Component
class HelloAssoTokenRefreshScheduler(private val executor: HelloAssoTokenRefreshExecutor) {

    @Scheduled(
        initialDelayString = "\${app.helloasso.token-refresh-initial-delay-ms:180000}",
        fixedDelayString = "\${app.helloasso.token-refresh-check-delay-ms:3600000}",
    )
    fun tick() = executor.sweep()
}

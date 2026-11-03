package org.commonlink.service

import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.repository.HelloAssoConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Hands out a valid HelloAsso access token for an association, from the encrypted cache in
 * [org.commonlink.entity.HelloAssoConnection] or by renewing the token pair.
 *
 * **One renewal at a time per association** — the row is read `FOR UPDATE` and the expiry re-checked once the lock is
 * held: HelloAsso rotates the refresh token on every renewal, so two concurrent renewals would leave one of them with
 * a dead token and break the chain. Same design as [MollieConnectTokenManager].
 *
 * A rejected refresh token surfaces as [HelloAssoUnauthorizedException] and nothing is written here: callers mark the
 * link BROKEN through [HelloAssoConnectionBreaker] once this transaction is over (scheduled renewal, checkout, re-read).
 */
@Service
class HelloAssoTokenManager(
    private val connectionRepo: HelloAssoConnectionRepository,
    private val client: HelloAssoGateway,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        /** A cached access token closer than this to its expiry is renewed first. */
        const val REFRESH_SAFETY_MARGIN_SECONDS = 60L
    }

    /**
     * Returns a valid access token for [associationId], renewing the pair when the cached one is missing or expires
     * within [REFRESH_SAFETY_MARGIN_SECONDS].
     *
     * @throws IllegalStateException when the association has no HelloAsso link or it is not ACTIVE.
     * @throws HelloAssoUnauthorizedException when HelloAsso rejects the refresh token (nothing is written).
     * @throws org.commonlink.exception.HelloAssoException on any other HelloAsso failure.
     */
    @Transactional
    fun getValidAccessToken(associationId: UUID): String {
        val connection = connectionRepo.findByAssociationIdForUpdate(associationId)
            ?: throw IllegalStateException("No HelloAsso link for association $associationId")
        check(connection.state == HelloAssoConnectionState.ACTIVE) {
            "HelloAsso link of association $associationId is ${connection.state}"
        }
        val now = Instant.now()
        val cached = connection.accessToken
        val expiresAt = connection.accessTokenExpiresAt
        if (cached != null && expiresAt != null && expiresAt.isAfter(now.plusSeconds(REFRESH_SAFETY_MARGIN_SECONDS))) {
            return cached
        }

        return renew(connection, now)
    }

    /**
     * Renews the token pair of [associationId] if its last renewal is still older than [threshold] once the row lock is
     * held — the scheduled weekly renewal; a second instance arriving after the first does nothing.
     *
     * @return true when a renewal happened.
     * @throws IllegalStateException when the association has no ACTIVE HelloAsso link.
     * @throws HelloAssoUnauthorizedException when HelloAsso rejects the refresh token (nothing is written).
     */
    @Transactional
    fun refreshIfOlderThan(associationId: UUID, threshold: Instant): Boolean {
        val connection = connectionRepo.findByAssociationIdForUpdate(associationId)
            ?: throw IllegalStateException("No HelloAsso link for association $associationId")
        check(connection.state == HelloAssoConnectionState.ACTIVE) {
            "HelloAsso link of association $associationId is ${connection.state}"
        }
        val last = connection.lastRefreshAt
        if (last != null && !last.isBefore(threshold)) return false
        renew(connection, Instant.now())
        return true
    }

    private fun renew(connection: org.commonlink.entity.HelloAssoConnection, now: Instant): String {
        val associationId = connection.association.id
        val refreshToken = checkNotNull(connection.refreshToken) {
            "HelloAsso link of association $associationId has no refresh token"
        }
        val tokens = client.refreshTokens(refreshToken)
        connection.accessToken = tokens.accessToken
        connection.refreshToken = tokens.refreshToken
        connection.accessTokenExpiresAt = now.plusSeconds(tokens.expiresIn)
        connection.lastRefreshAt = now
        connection.updatedAt = now
        connectionRepo.save(connection)
        logger.info("Renewed HelloAsso tokens for association {}", associationId)
        return tokens.accessToken
    }
}

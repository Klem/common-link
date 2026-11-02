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
 * Marking a link BROKEN, warning the association and the scheduled renewal belong to the renewal step (sprint step
 * S6-04); here a rejected refresh token surfaces as [HelloAssoUnauthorizedException] and nothing is written.
 */
@Service
class HelloAssoTokenManager(
    private val connectionRepo: HelloAssoConnectionRepository,
    private val client: HelloAssoClient,
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

package org.commonlink.service

import org.commonlink.config.MollieConnectConfig
import org.commonlink.entity.MollieConnectionState
import org.commonlink.event.MollieConnectionBrokenEvent
import org.commonlink.exception.MollieRefreshRejectedException
import org.commonlink.exception.MollieRefreshUnavailableException
import org.commonlink.repository.MollieConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.time.Instant

/**
 * Records the ACTIVE → BROKEN transition of a Mollie connection.
 *
 * ### Why this is its own bean
 * The write must run in a real transaction: it takes `SELECT … FOR UPDATE` and must commit. Kept as
 * a method on [MollieTokenRefreshExecutor] it would be reached by self-invocation
 * (`sweep` → `refreshOne` → `markBroken`), which never crosses the Spring proxy, so `@Transactional`
 * would be silently inert. Same trap, and the same remedy, as the extraction of
 * [MollieConnectTokenManager] out of [MollieConnectService].
 *
 * It also has to be a *separate* transaction rather than one wrapping the whole sweep: a sweep-wide
 * transaction would hold row locks on every association for its full duration, and one association's
 * failure would roll back the others.
 */
@Component
class MollieConnectionBreaker(
    private val connectionRepo: MollieConnectionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Marks the association's connection BROKEN and publishes [MollieConnectionBrokenEvent] once.
     *
     * Persisting here rather than inside the failed refresh is deliberate: throwing from
     * [MollieConnectTokenManager.getValidAccessToken] marks that transaction rollback-only, which is
     * why the previous in-place `state = BROKEN` never actually reached the database.
     *
     * Idempotent — the guard below plus the sweep's ACTIVE-only candidate query mean an association
     * cannot be flipped, or warned, twice for the same breakage.
     *
     * @param associationId Association whose Mollie authorisation Mollie has definitively rejected.
     * @param status HTTP status Mollie answered with, for the operator-facing log only.
     */
    @Transactional
    fun markBroken(associationId: UUID, status: Int) {
        val connection = connectionRepo.findByAssociationIdForUpdate(associationId) ?: return
        if (connection.state == MollieConnectionState.BROKEN) return
        connection.state = MollieConnectionState.BROKEN
        connectionRepo.save(connection)
        logger.warn(
            "Mollie connection marked BROKEN for association {} (refresh rejected, status={}) — " +
                "donations are now refused until the association re-authorises via the OAuth popup",
            associationId, status,
        )
        eventPublisher.publishEvent(MollieConnectionBrokenEvent(associationId))
    }
}

/**
 * Renews Mollie Connect access tokens before a donor ever needs them.
 *
 * ### Why this exists
 * Token refresh used to be purely lazy: nothing called it until a donation arrived. A revoked or
 * expired authorisation was therefore discovered *by the donor*, at which point the donation was
 * already lost and nobody had been told. This sweep moves that discovery hours or days earlier and
 * turns it into an email the association can act on. It also keeps the grant warm for associations
 * whose widget sees no traffic for weeks, so the refresh token is never left idle long enough to
 * lapse on Mollie's side.
 *
 * ### What it cannot do
 * It does **not** guarantee that donations stay possible. Once Mollie revokes the grant — the
 * association withdraws access from its dashboard, the organisation is closed — no refresh cadence
 * recovers it, and re-authorisation through the OAuth popup is the only way back. Early warning is
 * the whole benefit.
 *
 * ### Concurrency
 * Per-connection safety is inherited, not reimplemented: the sweep calls
 * [MollieConnectTokenManager.getValidAccessToken], which takes `SELECT … FOR UPDATE` on the row and
 * re-checks the expiry *after* acquiring the lock. Two application instances sweeping at once
 * therefore cannot double-refresh — the loser sees a token that is already fresh and issues no HTTP
 * call. No distributed lock is needed.
 *
 * Split from [MollieTokenRefreshScheduler] on the same rationale as
 * [SanctionSyncExecutor]/[SanctionSyncScheduler]: the logic stays directly invocable from tests and
 * operational tooling even when the schedule is disabled.
 */
@Service
class MollieTokenRefreshExecutor(
    private val connectionRepo: MollieConnectionRepository,
    private val tokenManager: MollieConnectTokenManager,
    private val connectionBreaker: MollieConnectionBreaker,
    private val config: MollieConnectConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Refreshes every ACTIVE connection whose token expires within the configured lookahead.
     *
     * Each connection is isolated in its own try/catch: one association with a dead grant must not
     * stop the others from being renewed. Never throws — a sweep failure is an operational event,
     * not something to propagate into the scheduler thread.
     */
    fun sweep() {
        val lookahead = config.tokenRefresh.lookaheadSeconds
        val candidates = connectionRepo.findActiveExpiringBefore(Instant.now().plusSeconds(lookahead))
        if (candidates.isEmpty()) {
            logger.debug("Mollie token refresh: no connection expiring within {}s", lookahead)
            return
        }

        var refreshed = 0
        var broken = 0
        var deferred = 0
        var skipped = 0
        for (candidate in candidates) {
            // Mock connections carry a sentinel refresh token that Mollie can only reject. They are
            // filtered here rather than in the query because the token column is encrypted with a
            // random IV in production, making a SQL comparison meaningless. DEBUG, not WARN: local
            // and staging keep mock rows around forever and would otherwise warn on every tick.
            if (candidate.refreshToken == MOCK_TOKEN_SENTINEL) {
                logger.debug(
                    "Mollie token refresh: skipping mock connection for association {}",
                    candidate.associationId,
                )
                skipped++
                continue
            }
            when (refreshOne(candidate.associationId, lookahead)) {
                Outcome.REFRESHED -> refreshed++
                Outcome.BROKEN -> broken++
                Outcome.DEFERRED -> deferred++
            }
        }
        logger.info(
            "Mollie token refresh sweep: {} candidate(s) — refreshed={} broken={} deferred={} mock-skipped={}",
            candidates.size, refreshed, broken, deferred, skipped,
        )
    }

    private fun refreshOne(associationId: UUID, lookaheadSeconds: Long): Outcome = try {
        tokenManager.getValidAccessToken(associationId, lookaheadSeconds)
        Outcome.REFRESHED
    } catch (ex: MollieRefreshRejectedException) {
        // Mollie will never accept this refresh token again — record it so every donation gate
        // closes at once and the association is told to re-authorise.
        connectionBreaker.markBroken(associationId, ex.status.value())
        Outcome.BROKEN
    } catch (ex: MollieRefreshUnavailableException) {
        // Throttling or a Mollie outage says nothing about the grant. Leave the connection ACTIVE
        // and let the next tick retry; the donation path still works until the token lapses.
        logger.warn("Mollie token refresh deferred for association {}: {}", associationId, ex.message)
        Outcome.DEFERRED
    } catch (ex: IllegalStateException) {
        // Connection vanished or turned BROKEN between the query and the lock — nothing to do.
        logger.debug("Mollie token refresh skipped for association {}: {}", associationId, ex.message)
        Outcome.DEFERRED
    }

    /** Per-connection result, aggregated into the single end-of-sweep log line. */
    private enum class Outcome { REFRESHED, BROKEN, DEFERRED }
}

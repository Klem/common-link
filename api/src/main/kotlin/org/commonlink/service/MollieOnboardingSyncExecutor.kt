package org.commonlink.service

import org.commonlink.config.MollieConnectConfig
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieOnboardingSyncCandidate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Polls Mollie for the onboarding status of every connection that does not collect donations yet.
 *
 * ### Why this exists
 * Mollie has no onboarding webhook (D2). Without a sweep, a dossier only moves forward on the
 * CommonLink side when the association reopens its dashboard: an IN_REVIEW phase that starts and
 * ends between two visits is never observed, so "complement requested" would be unreliable, and a
 * validation would not unlock the Widget/Landing tabs (nor send the existing transition emails)
 * until the next visit.
 *
 * ### Scope
 * Only ACTIVE connections that do not collect ([isCollecting] false: NEEDS_DATA, IN_REVIEW, or
 * COMPLETED without payments). A connection that collects is never re-polled (sprint 20, Q1). Mock
 * connections are skipped silently. `app.mollie.connect.disable-onboarding-sync` is the only switch:
 * when true (local, staging) the sweep does nothing.
 *
 * ### Cadence
 * Each candidate is due when its last sync is older than [recheckIntervalSeconds] (5 min, or 1 h
 * for a connection in NEEDS_DATA for more than 7 days). The scheduler ticks far more often than
 * that; a tick only calls Mollie for the due candidates.
 *
 * Split from [MollieOnboardingSyncScheduler] on the same rationale as
 * [MollieTokenRefreshExecutor]/[MollieTokenRefreshScheduler]: the logic stays directly invocable
 * from tests and operational tooling.
 */
@Service
class MollieOnboardingSyncExecutor(
    private val connectionRepo: MollieConnectionRepository,
    private val mollieConnectService: MollieConnectService,
    private val config: MollieConnectConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Syncs every due candidate.
     *
     * Each connection is isolated in its own try/catch: one failing association (network error,
     * rejected token) must not stop the others. Never throws — a sweep failure is an operational
     * event, not something to propagate into the scheduler thread. One WARN per failure, one INFO
     * summary when at least one connection was due.
     */
    fun sweep() {
        if (config.disableOnboardingSync) {
            logger.debug("Mollie onboarding sync: disabled (disable-onboarding-sync=true)")
            return
        }
        val now = Instant.now()
        val candidates = connectionRepo.findActiveNotCollecting()
        var synced = 0
        var failed = 0
        var notDue = 0
        var mockSkipped = 0
        for (candidate in candidates) {
            // Same reasoning as MollieTokenRefreshExecutor.sweep(): the token column is encrypted
            // with a random IV, so mock rows can only be recognised in Kotlin. DEBUG, not WARN.
            if (candidate.refreshToken == MOCK_TOKEN_SENTINEL) {
                logger.debug(
                    "Mollie onboarding sync: skipping mock connection for association {}",
                    candidate.associationId,
                )
                mockSkipped++
                continue
            }
            // Already excluded by the query; re-checked here so the Q1 guarantee does not rest on
            // JPQL alone (a connection that collects must never reach Mollie from this sweep).
            if (isCollecting(candidate.onboardingStatus, candidate.canReceivePayments)) {
                notDue++
                continue
            }
            if (!isDue(candidate, now)) {
                notDue++
                continue
            }
            try {
                mollieConnectService.syncOnboardingStatus(candidate.associationId)
                synced++
            } catch (ex: Exception) {
                failed++
                logger.warn(
                    "Mollie onboarding sync failed for association {} via {} API: {}",
                    candidate.associationId, config.onboardingApi, ex.message,
                )
            }
        }
        if (synced + failed > 0) {
            logger.info(
                "Mollie onboarding sync sweep: {} candidate(s) — synced={} failed={} not-due={} mock-skipped={}",
                candidates.size, synced, failed, notDue, mockSkipped,
            )
        } else {
            logger.debug(
                "Mollie onboarding sync sweep: {} candidate(s), none due (mock-skipped={})",
                candidates.size, mockSkipped,
            )
        }
    }

    /**
     * True when [candidate] has never been synced or its last sync is at least
     * [recheckIntervalSeconds] old at [now].
     */
    internal fun isDue(candidate: MollieOnboardingSyncCandidate, now: Instant): Boolean {
        val lastSyncedAt = candidate.lastSyncedAt ?: return true
        val interval = recheckIntervalSeconds(candidate.onboardingStatus, candidate.onboardingStatusSince, now)
        return !lastSyncedAt.isAfter(now.minusSeconds(interval))
    }

    /**
     * The sweep cadence (D4), in one place: a connection in NEEDS_DATA for longer than
     * `dormant-after-days` (7) is rechecked every `dormant-recheck-seconds` (1 h); every other
     * candidate (NEEDS_DATA more recent, IN_REVIEW, COMPLETED without payments) every
     * `pending-recheck-seconds` (5 min).
     *
     * @param status Current onboarding status of the connection.
     * @param statusSince When that status was first observed.
     * @param now Reference instant.
     * @return minimum number of seconds between two syncs of this connection.
     */
    internal fun recheckIntervalSeconds(
        status: MollieOnboardingStatus,
        statusSince: Instant,
        now: Instant,
    ): Long {
        val sync = config.onboardingSync
        val dormant = status == MollieOnboardingStatus.NEEDS_DATA &&
            statusSince.isBefore(now.minus(Duration.ofDays(sync.dormantAfterDays)))
        return if (dormant) sync.dormantRecheckSeconds else sync.pendingRecheckSeconds
    }
}

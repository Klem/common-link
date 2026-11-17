package org.commonlink.service

import jakarta.annotation.PreDestroy
import org.commonlink.config.PaymentsProperties
import org.commonlink.repository.HelloAssoCashOutSyncRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Result of an on-demand refresh of the cash-out tracking (Payments tab).
 *
 * @property result The tracking outcome after the refresh, or the known one when no pass ran or it is still running.
 * @property refreshed Whether this request ran a pass to completion.
 * @property nextRefreshAvailableAt From when a new request can run a pass.
 */
data class CashOutRefreshOutcome(
    val result: CashOutSyncResult,
    val refreshed: Boolean,
    val nextRefreshAvailableAt: Instant,
)

/**
 * On-demand refresh of an association's cash-out tracking, from its Payments tab (user decision 10/11: possible, with
 * protection against repeated clicks).
 *
 * - At most one pass per association per `app.payments.helloasso.cashout-refresh-min-interval` (15 minutes by
 *   default), whatever starts it: this request, another campaign of the same association, or the scheduled pass
 *   (which stamps the same column).
 * - The guard is one conditional UPDATE in the database ([HelloAssoCashOutSyncRepository.claimRefresh]), so it holds
 *   across instances: of several simultaneous requests, one runs the pass.
 * - A request over the limit is not an error: it gets the known state and [CashOutRefreshOutcome.nextRefreshAvailableAt]
 *   (never a 429, which the frontend would turn into a generic error toast).
 * - The pass runs on a small dedicated pool and is awaited at most `cashout-refresh-timeout` (10 s by default);
 *   beyond, the request answers with the known state and the pass finishes in the background.
 */
@Service
class HelloAssoCashOutRefreshService(
    private val syncRepository: HelloAssoCashOutSyncRepository,
    private val executor: HelloAssoCashOutSyncExecutor,
    private val properties: PaymentsProperties,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Time source; replaced by tests. */
    internal var clock: Clock = Clock.systemUTC()

    /** Runs the on-demand passes; two threads, daemon so they never hold the JVM. */
    private val pool: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "helloasso-cashout-refresh").apply { isDaemon = true }
    }

    /** Requests a pass for [associationId]; see the class documentation for the limits. Never throws for a click too many. */
    fun requestRefresh(associationId: UUID): CashOutRefreshOutcome {
        val settings = properties.helloasso
        // PostgreSQL keeps microseconds: the claimer and the requests reading its stamp back see the same instant.
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val claimed = transactionTemplate.execute {
            syncRepository.insertIfMissing(associationId)
            syncRepository.claimRefresh(associationId, now, now.minus(settings.cashoutRefreshMinInterval)) == 1
        } == true
        if (!claimed) {
            val last = syncRepository.findById(associationId).map { it.lastRefreshRequestedAt }.orElse(null) ?: now
            log.debug("HelloAsso cash-out refresh of association {} skipped: last pass at {}", associationId, last)
            return CashOutRefreshOutcome(executor.resultOf(associationId), false, last.plus(settings.cashoutRefreshMinInterval))
        }
        val next = now.plus(settings.cashoutRefreshMinInterval)
        val pass = CompletableFuture.supplyAsync({ executor.syncAssociation(associationId) }, pool)
        return try {
            CashOutRefreshOutcome(pass.get(settings.cashoutRefreshTimeout.toMillis(), TimeUnit.MILLISECONDS), true, next)
        } catch (ex: TimeoutException) {
            log.info("HelloAsso cash-out refresh of association {} still running after {}", associationId, settings.cashoutRefreshTimeout)
            CashOutRefreshOutcome(executor.resultOf(associationId), false, next)
        } catch (ex: Exception) {
            log.error("HelloAsso cash-out refresh of association {} failed: {}", associationId, ex.javaClass.simpleName)
            CashOutRefreshOutcome(executor.resultOf(associationId), false, next)
        }
    }

    @PreDestroy
    fun shutdown() {
        pool.shutdownNow()
    }
}

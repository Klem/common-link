package org.commonlink.service

import org.commonlink.config.BridgeProperties
import org.commonlink.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Re-reads, from Bridge, the payouts whose state has stopped moving.
 *
 * ### Why this exists at all
 * Settlement used to be webhook-only, on the reasoning that Bridge retries a notification for one
 * to two days on a non-2xx. That holds for a notification we *reject*; it says nothing about one we
 * accept and then fail to apply. Three payouts were stranded exactly that way on 23 September
 * 2026: Bridge announced a link's expiry, the handler answered `200`, the state resolution of the
 * day discarded it, and Bridge — correctly — never sent it again. No retry recovers a notification
 * the sender considers delivered.
 *
 * The second reason is not a bug of ours at all. Bridge documents that some banks (LCL, Nickel)
 * **never report an execution status**: their flow stops at `PDNG` and `ACSC` never comes. Such a
 * payout would sit engaged for ever, its amount unavailable to the campaign and no attestation ever
 * published, while the money has in fact left the account.
 *
 * ### What it does, and deliberately does not do
 * It re-reads and replays through [BridgeWebhookService], so settle, fail and release keep exactly
 * one implementation. The sweep decides nothing itself.
 *
 * It never promotes a payout on a guess. Past [BridgeProperties.Reconciler.stuckAfter] with no
 * terminal answer, it raises [TechnicalAlertKind.PAYOUT_STUCK_IN_FLIGHT] and stops there: the
 * on-chain attestation is irretractable, and this whole flow certifies only what it has observed.
 * For a bank that reports nothing, the observation has to come from a human reading a statement.
 *
 * @param payouts Source of the stale rows.
 * @param webhookService The one implementation of "apply Bridge's current view to a payout".
 * @param alerts Where a payout nobody can resolve is escalated.
 * @param props Bridge configuration; the thresholds live under `app.bridge.reconciler`.
 */
@Component
class BridgePayoutReconciler(
    private val payouts: PayoutRepository,
    private val webhookService: BridgeWebhookService,
    private val alerts: TechnicalAlertService,
    private val props: BridgeProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Re-reads every payout engaged without news for longer than
     * [BridgeProperties.Reconciler.staleAfter], then escalates those nothing can resolve.
     *
     * Each payout is handled independently and a failure on one never stops the sweep: an
     * unreachable Bridge would otherwise leave the rest unexamined until the next tick. Rows come
     * oldest first so a sweep cut short makes progress on the worst cases.
     *
     * Disabled in demo mode, where Bridge is never called and `getPaymentLink` answers `ACSC`
     * unconditionally — sweeping there would settle every pending payout on a simulation.
     */
    @Scheduled(
        fixedDelayString = "\${app.bridge.reconciler.fixed-delay:PT30M}",
        initialDelayString = "\${app.bridge.reconciler.initial-delay:PT2M}",
    )
    fun sweep() {
        val config = props.reconciler
        if (!config.enabled || props.demoMode) return

        val now = Instant.now()
        val stale = payouts.findStaleInFlight(now.minus(config.staleAfter))
        if (stale.isEmpty()) return

        val stuckThreshold = now.minus(config.stuckAfter)
        var reRead = 0
        var stuck = 0

        stale.forEach { payout ->
            runCatching {
                webhookService.handlePaymentLinkNotification(payout.bridgePaymentLinkId, payout.id.toString())
            }
                .onSuccess { reRead++ }
                .onFailure { log.warn("Bridge reconciliation failed for payout {}: {}", payout.id, it.message) }

            // The age is the one read *before* the replay: applying a state refreshes
            // `bridgeSyncedAt`, so a payout that just got re-stamped PDNG would otherwise look new.
            val syncedAt = payout.bridgeSyncedAt
            if (syncedAt != null && syncedAt.isBefore(stuckThreshold) && isBeyondAuthorisation(payout.id)) {
                log.error(
                    "Payout {} has been in flight at Bridge since {} with no terminal answer — some banks " +
                        "never report an execution status, so this needs a human to confirm from the statement",
                    payout.id, syncedAt,
                )
                stuck++
            }
        }

        log.info("Bridge reconciliation: {} payout(s) re-read, {} still unresolved past the threshold", reRead, stuck)
        if (stuck > 0) {
            // One alert per sweep, not per payout: the operator needs to know the condition exists,
            // not to receive it once per row.
            alerts.reportFailure(TechnicalAlertKind.PAYOUT_STUCK_IN_FLIGHT, null, "BridgePayoutReconciler", null)
        }
    }

    /**
     * Whether the payout, re-read after the replay, is still past authorisation and unresolved.
     *
     * Only `PDNG` and `PART` qualify. A payout left at `CREA` or `ACTC` is waiting on an
     * association that has not authorised, which its link's expiry resolves on its own —
     * escalating that would report ordinary hesitation as an incident.
     */
    private fun isBeyondAuthorisation(payoutId: java.util.UUID): Boolean {
        val current = payouts.findRoutingById(payoutId) ?: return false
        val status = current.bridgeStatus ?: return false
        return status.survivesLinkDeath && status.isInFlight
    }
}

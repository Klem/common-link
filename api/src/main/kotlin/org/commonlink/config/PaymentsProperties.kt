package org.commonlink.config

import org.commonlink.payment.PaymentProviderId
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration of the payment service providers that collect donations.
 *
 * **Default provider** — [defaultProvider] is written to
 * [org.commonlink.entity.AssociationProfile.paymentProvider] when an association is created, and never read
 * again afterwards: changing it only affects associations created from then on. New associations go to HelloAsso;
 * Mollie is set by hand by the curator, on an association's specific request. Associations created before the
 * HelloAsso support stay on Mollie (migration V87).
 *
 * @property defaultProvider Payment provider given to a newly created association.
 * @property helloasso Cash-out tracking of HelloAsso donations (`app.payments.helloasso`).
 */
@ConfigurationProperties(prefix = "app.payments")
data class PaymentsProperties(
    val defaultProvider: PaymentProviderId = PaymentProviderId.HELLOASSO,
    val helloasso: HelloAssoCashOutProperties = HelloAssoCashOutProperties(),
)

/**
 * Cash-out tracking of HelloAsso donations: when the money of a donation HelloAsso paid out is considered arrived on
 * the association's bank account, and how often HelloAsso is read. Days are TARGET2 business days
 * ([org.commonlink.service.BusinessDayCalendar]).
 *
 * @property cashoutSettlementBusinessDays Delay after HelloAsso's payout date after which a paid-out donation becomes
 *   available on its own. **Provisional value** (CEO decision Q1: 2 business days, adjustable): to revisit once
 *   HelloAsso confirms whether its date is the emission or the expected credit (question U1).
 * @property cashoutInTransitAlertBusinessDays A donation paid out for longer than this without becoming available
 *   raises a technical alert (default 5).
 * @property cashoutSyncInitialDelayMs Delay before the first scheduled tracking pass after startup.
 * @property cashoutSyncDelayMs Delay between two scheduled tracking passes (24 hours).
 * @property cashoutRefreshMinInterval At most one tracking pass per association within this interval, on-demand
 *   refreshes and scheduled passes combined (default 15 minutes).
 * @property cashoutRefreshTimeout How long an on-demand refresh waits for its pass before answering with the known
 *   state; the pass goes on in the background.
 * @property cashoutUnknownAlertAfter An `Unknown` (or unrecognised) payout state persisting this long raises a
 *   technical alert. Default 3 days, proposed (no product decision): long enough to ignore a transient value.
 * @property cashoutSyncFailureAlertAfter Consecutive failed passes of one association that raise a technical alert.
 * @property cashoutExportAmountUnit Unit of `credit` / `debit` in HelloAsso's payout export. **Provisional default**
 *   [CashOutExportAmountUnit.EUROS] (the SDK types them as floats), until HelloAsso answers question U3.
 */
data class HelloAssoCashOutProperties(
    val cashoutSettlementBusinessDays: Int = 2,
    val cashoutInTransitAlertBusinessDays: Int = 5,
    val cashoutSyncInitialDelayMs: Long = 300_000,
    val cashoutSyncDelayMs: Long = 86_400_000,
    val cashoutRefreshMinInterval: Duration = Duration.ofMinutes(15),
    val cashoutRefreshTimeout: Duration = Duration.ofSeconds(10),
    val cashoutUnknownAlertAfter: Duration = Duration.ofDays(3),
    val cashoutSyncFailureAlertAfter: Int = 3,
    val cashoutExportAmountUnit: CashOutExportAmountUnit = CashOutExportAmountUnit.EUROS,
) {
    init {
        require(cashoutSettlementBusinessDays >= 0) { "cashout-settlement-business-days must not be negative" }
        require(cashoutInTransitAlertBusinessDays > 0) { "cashout-in-transit-alert-business-days must be positive" }
        require(cashoutSyncFailureAlertAfter > 0) { "cashout-sync-failure-alert-after must be positive" }
    }
}

/** Unit of the amounts of HelloAsso's payout export (question U3). */
enum class CashOutExportAmountUnit {
    /** Decimal euros (`10.5` = 10.50 €). */
    EUROS,

    /** Integer cents (`1050` = 10.50 €), like HelloAsso's payment amounts. */
    CENTS,
}

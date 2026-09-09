package org.commonlink.controller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.BridgeWebhookService
import org.commonlink.service.TechnicalAlertKind
import org.commonlink.service.TechnicalAlertService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Bridge webhook payload.
 *
 * Only [paymentLinkId] is used — everything else is untrusted and deliberately ignored, the state
 * being re-read from Bridge (see [BridgeWebhookService]). Kept as a typed class rather than a map
 * so the one field being consumed is explicit.
 *
 * @param type Event type, e.g. `payment.link.updated`, `payment.transaction.updated`, `TEST_EVENT`.
 * @param paymentLinkId Bridge payment-link id; absent on a `TEST_EVENT` from the dashboard.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BridgeWebhookPayload(
    val type: String?,
    @JsonProperty("payment_link_id") val paymentLinkId: String?,
)

/**
 * Receives Bridge payment status notifications — the only mechanism driving payout settlement.
 *
 * **Response contract, and why it differs from [MollieWebhookController].** Mollie's webhook is
 * answered 200 unconditionally to avoid a retry storm, with a reconciler as the backstop. Here
 * there is no reconciler and no polling: this notification is the single path by which a payout
 * ever becomes CONFIRMED. So a processing failure answers **502**, which makes Bridge retry for one
 * to two days with exponential backoff — losing the notification would strand the payout in
 * PENDING with its balance engaged, while a duplicate is harmless
 * ([org.commonlink.service.PayoutConfirmer.finaliseSettled] is idempotent).
 *
 * Security: no authentication (covered by permitAll on the `/api/public` prefix). Bridge documents
 * no signature scheme, so authenticity is **not** derived from the request: the body is treated as
 * a bare trigger and the payment state is re-fetched from Bridge. An attacker posting a forged
 * notification can therefore only cause a redundant read of a payment link they must already know
 * the id of; they cannot move a payout to a state Bridge does not report.
 *
 * @property technicalAlertServiceProvider Resolved lazily and optionally, same rationale as
 *   [MollieWebhookController]: a `@WebMvcTest` slice declares no alerting bean.
 */
@RestController
@RequestMapping("/api/public/webhooks")
@Tag(name = "Webhooks")
class BridgeWebhookController(
    private val bridgeWebhookService: BridgeWebhookService,
    private val technicalAlertServiceProvider: ObjectProvider<TechnicalAlertService>,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handles a Bridge notification.
     *
     * @param payload The notification; only its payment-link id is consumed.
     * @return 200 once processed (or when there is nothing to process), 502 on failure so Bridge
     *   retries.
     */
    @PostMapping("/bridge")
    @Operation(summary = "Bridge payment status webhook")
    fun handleBridgeWebhook(
        @RequestBody payload: BridgeWebhookPayload,
    ): ResponseEntity<Void> {
        val paymentLinkId = payload.paymentLinkId
        if (paymentLinkId.isNullOrBlank()) {
            // The dashboard's "Send a test" button posts a TEST_EVENT with no payment link.
            logger.info("Received Bridge webhook type={} with no payment_link_id — nothing to do", payload.type)
            return ResponseEntity.ok().build()
        }

        logger.info("Received Bridge webhook type={} paymentLinkId={}", payload.type, paymentLinkId)
        return try {
            bridgeWebhookService.handlePaymentLinkNotification(paymentLinkId)
            ResponseEntity.ok().build()
        } catch (ex: Exception) {
            logger.error("Bridge webhook processing error for paymentLinkId={}", paymentLinkId, ex)
            try {
                technicalAlertServiceProvider.ifAvailable?.reportFailure(
                    TechnicalAlertKind.WEBHOOK_PROCESSING_FAILURE,
                    "POST",
                    "/api/public/webhooks/bridge",
                    ex,
                )
            } catch (alertEx: Exception) {
                logger.error("Failed to report Bridge webhook failure", alertEx)
            }
            // Non-2xx on purpose: Bridge must retry, this is the only path to settlement.
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
        }
    }
}

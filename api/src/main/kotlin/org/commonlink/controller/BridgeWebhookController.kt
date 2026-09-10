package org.commonlink.controller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.BridgeWebhookService
import org.commonlink.service.BridgeWebhookSignatureVerifier
import org.commonlink.service.TechnicalAlertKind
import org.commonlink.service.TechnicalAlertService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Bridge webhook payload.
 *
 * [paymentLinkId] is the primary key used to find the payout; [clientReference] (the payoutId we
 * sent at creation, see [org.commonlink.service.BridgePaymentInitiationService.createPaymentLink])
 * is a fallback for `payment.transaction.*` events, which document `payment_link_id` as optional.
 * Everything else is untrusted and deliberately ignored, the state being re-read from Bridge (see
 * [BridgeWebhookService]).
 *
 * @param type Event type, e.g. `payment.link.updated`, `payment.transaction.updated`, `TEST_EVENT`.
 * @param paymentLinkId Bridge payment-link id; absent on a `TEST_EVENT` from the dashboard, and
 *   documented optional on `payment.transaction.created`/`.updated`.
 * @param clientReference `client_reference` on a transaction event — the payoutId, as a string.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BridgeWebhookPayload(
    val type: String?,
    @JsonProperty("payment_link_id") val paymentLinkId: String?,
    @JsonProperty("client_reference") val clientReference: String?,
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
 * ([org.commonlink.service.PayoutConfirmer.finaliseSettled] is idempotent). An invalid signature is
 * a different failure mode entirely — see below — and answers **401**, not 502: Bridge must not
 * retry forged garbage for two days.
 *
 * Security: two independent layers, deliberately not conflated. (1) [BridgeWebhookSignatureVerifier]
 * checks the `BridgeApi-Signature` header (HMAC-SHA256 over the raw body) before anything else runs
 * — this is what Bridge actually documents, contrary to this controller's previous assumption that
 * no signature scheme existed. (2) Regardless of that outcome, the body is still never trusted for
 * *content*: [BridgeWebhookService] re-reads the authoritative state from Bridge itself rather than
 * acting on whatever the notification claims. So a forged-but-signed-looking call that slips past
 * (1) — impossible without the secret — still cannot move a payout to a state Bridge does not
 * actually report.
 *
 * @property technicalAlertServiceProvider Resolved lazily and optionally, same rationale as
 *   [MollieWebhookController]: a `@WebMvcTest` slice declares no alerting bean.
 */
@RestController
@RequestMapping("/api/public/webhooks")
@Tag(name = "Webhooks")
class BridgeWebhookController(
    private val bridgeWebhookService: BridgeWebhookService,
    private val signatureVerifier: BridgeWebhookSignatureVerifier,
    private val objectMapper: ObjectMapper,
    private val technicalAlertServiceProvider: ObjectProvider<TechnicalAlertService>,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handles a Bridge notification.
     *
     * @param rawBody The exact request body, needed unmodified for signature verification —
     *   binding straight to [BridgeWebhookPayload] would only expose the re-serialised form, which
     *   Bridge never signed.
     * @param signatureHeader Value of the `BridgeApi-Signature` header, if present.
     * @return 401 if the signature is missing/invalid, 200 once processed (or when there is
     *   nothing to process), 502 on processing failure so Bridge retries.
     */
    @PostMapping("/bridge")
    @Operation(summary = "Bridge payment status webhook")
    fun handleBridgeWebhook(
        @RequestBody rawBody: String,
        @RequestHeader(name = "BridgeApi-Signature", required = false) signatureHeader: String?,
    ): ResponseEntity<Void> {
        if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val payload = try {
            objectMapper.readValue(rawBody, BridgeWebhookPayload::class.java)
        } catch (ex: Exception) {
            logger.warn("Bridge webhook body could not be parsed: {}", ex.message)
            return ResponseEntity.ok().build()
        }

        val paymentLinkId = payload.paymentLinkId
        val clientReference = payload.clientReference
        if (paymentLinkId.isNullOrBlank() && clientReference.isNullOrBlank()) {
            // The dashboard's "Send a test" button posts a TEST_EVENT with neither field set.
            logger.info("Received Bridge webhook type={} with no payment_link_id/client_reference — nothing to do", payload.type)
            return ResponseEntity.ok().build()
        }

        logger.info(
            "Received Bridge webhook type={} paymentLinkId={} clientReference={}",
            payload.type, paymentLinkId, clientReference,
        )
        return try {
            bridgeWebhookService.handlePaymentLinkNotification(paymentLinkId, clientReference)
            ResponseEntity.ok().build()
        } catch (ex: Exception) {
            logger.error("Bridge webhook processing error for paymentLinkId={} clientReference={}", paymentLinkId, clientReference, ex)
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

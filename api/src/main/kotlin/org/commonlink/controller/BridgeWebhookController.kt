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
 * Identifying fields of a Bridge webhook, which the API nests under `content`.
 *
 * [paymentLinkId] is the primary key used to find the payout. The reference — the payoutId we sent
 * at creation, see [org.commonlink.service.BridgePaymentInitiationService.createPaymentLink] — is a
 * fallback for `payment.transaction.*` events, which document `payment_link_id` as optional. Bridge
 * names it differently per event: `client_reference` on a transaction event,
 * `payment_link_client_reference` on `payment.link.updated`. Both are read; neither is trusted for
 * anything beyond routing, the state being re-read from Bridge (see [BridgeWebhookService]).
 *
 * [paymentRequestId] names *which* payment request this notification is about. A link can carry
 * several — Bridge does not burn it on a rejection, so a second authorisation creates a second
 * request — and listing them back gives no usable order, so naming the one that moved is the only
 * unambiguous way to read the right state. It is used for addressing only, exactly like
 * [paymentLinkId]: the body says which resource to look at, Bridge's API says what its state is.
 *
 * @param paymentLinkId Bridge payment-link id, `content.payment_link_id`.
 * @param paymentRequestId Bridge payment-request id, `content.payment_request_id`.
 * @param clientReference `content.client_reference` — the payoutId, as a string.
 * @param paymentLinkClientReference `content.payment_link_client_reference`, same value under the
 *   name `payment.link.updated` uses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BridgeWebhookContent(
    @JsonProperty("payment_link_id") val paymentLinkId: String?,
    @JsonProperty("payment_request_id") val paymentRequestId: String?,
    @JsonProperty("client_reference") val clientReference: String?,
    @JsonProperty("payment_link_client_reference") val paymentLinkClientReference: String?,
) {
    /** The payoutId Bridge echoes back, whichever name this event type carries it under. */
    val reference: String? get() = clientReference?.takeIf { it.isNotBlank() } ?: paymentLinkClientReference
}

/**
 * Bridge webhook payload.
 *
 * Only `type` and `timestamp` sit at the root; every identifying field is nested under `content`.
 * Reading them at the root instead made every genuine settlement notification deserialise to nulls
 * and be answered `200` as "nothing to do", stranding payouts in `CREA` for good — Bridge counts a
 * `200` as delivered and never retries. There is deliberately no root-level fallback: Bridge does
 * not send that shape, and accepting it would only make the mistake survivable in silence again.
 *
 * @param type Event type, e.g. `payment.link.updated`, `payment.transaction.updated`, `TEST_EVENT`.
 * @param content Identifying fields; absent on a `TEST_EVENT` from the dashboard.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BridgeWebhookPayload(
    val type: String?,
    val content: BridgeWebhookContent?,
)

/** Type of the notification the Bridge dashboard's "Send a test" button emits, which carries no content. */
private const val TEST_EVENT_TYPE = "TEST_EVENT"

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

        val paymentLinkId = payload.content?.paymentLinkId
        val clientReference = payload.content?.reference
        if (paymentLinkId.isNullOrBlank() && clientReference.isNullOrBlank()) {
            if (TEST_EVENT_TYPE.equals(payload.type, ignoreCase = true)) {
                // The dashboard's "Send a test" button posts a TEST_EVENT with no content.
                logger.info("Received Bridge webhook type={} — nothing to do", payload.type)
            } else {
                // Anything else carrying no routable id is a settlement notification being dropped,
                // and Bridge will not send it again once this answers 200. Log loudly: the silent
                // version of this branch is what hid a payload-shape mismatch until payouts were
                // found stuck in CREA.
                logger.warn(
                    "Received Bridge webhook type={} with no content.payment_link_id and no client " +
                        "reference — nothing to route, notification dropped",
                    payload.type,
                )
            }
            return ResponseEntity.ok().build()
        }

        logger.info(
            "Received Bridge webhook type={} paymentLinkId={} clientReference={}",
            payload.type, paymentLinkId, clientReference,
        )
        return try {
            bridgeWebhookService.handlePaymentLinkNotification(
                paymentLinkId, clientReference, payload.content?.paymentRequestId,
            )
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

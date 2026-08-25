package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.MollieWebhookService
import org.commonlink.service.TechnicalAlertKind
import org.commonlink.service.TechnicalAlertService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Receives Mollie payment status change notifications.
 *
 * Mollie sends a form-urlencoded POST with a single `id` field.
 * We always respond 200 to Mollie regardless of processing outcome, to avoid a retry storm —
 * a processing failure is logged **and** reported to [TechnicalAlertService]. That still leaves
 * one failure mode unaddressed by this controller: a webhook that Mollie never manages to
 * deliver at all (e.g. an unreachable `webhookUrl`) never reaches this method, so it can raise no
 * alert here. [org.commonlink.service.MolliePaymentReconciler] is the backstop for that case — it
 * periodically re-checks stale-pending donations directly against Mollie's API and self-heals
 * them, alerting separately if it had to.
 *
 * Security: no authentication required (covered by permitAll on the /api/public prefix).
 * Authenticity is guaranteed by re-fetching the payment from Mollie (never trusting the body).
 *
 * @property technicalAlertServiceProvider Resolved lazily and optionally, same rationale as
 *   [org.commonlink.exception.GlobalExceptionHandler]: `@WebMvcTest(MollieWebhookController::class)`
 *   does not declare an alerting bean, and an [ObjectProvider] lets the controller keep working —
 *   silently, without alerts — in that slice instead of forcing a mock into every test.
 */
@RestController
@RequestMapping("/api/public/webhooks")
@Tag(name = "Webhooks")
class MollieWebhookController(
    private val mollieWebhookService: MollieWebhookService,
    private val technicalAlertServiceProvider: ObjectProvider<TechnicalAlertService>,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/mollie")
    @Operation(summary = "Mollie payment webhook")
    fun handleMollieWebhook(
        @RequestParam("id") paymentId: String,
    ): ResponseEntity<Void> {
        logger.info("Received Mollie webhook for paymentId={}", paymentId)
        try {
            mollieWebhookService.handleWebhook(paymentId)
        } catch (ex: Exception) {
            logger.error("Mollie webhook processing error for paymentId={}", paymentId, ex)
            try {
                technicalAlertServiceProvider.ifAvailable?.reportFailure(
                    TechnicalAlertKind.WEBHOOK_PROCESSING_FAILURE,
                    "POST",
                    "/api/public/webhooks/mollie",
                    ex,
                )
            } catch (e: Exception) {
                logger.warn("Technical alert WEBHOOK_PROCESSING_FAILURE could not be raised: {}", e.javaClass.simpleName)
            }
        }
        return ResponseEntity.ok().build()
    }
}

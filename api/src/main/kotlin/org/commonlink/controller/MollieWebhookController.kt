package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.MollieWebhookService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Receives Mollie payment status change notifications.
 *
 * Mollie sends a form-urlencoded POST with a single `id` field.
 * We always respond 200 to Mollie regardless of processing outcome — failures are
 * logged and recovered by the scheduled reconciler in DonationReceiptService.
 *
 * Security: no authentication required (covered by permitAll on the /api/public prefix).
 * Authenticity is guaranteed by re-fetching the payment from Mollie (never trusting the body).
 */
@RestController
@RequestMapping("/api/public/webhooks")
@Tag(name = "Webhooks")
class MollieWebhookController(
    private val mollieWebhookService: MollieWebhookService,
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
        }
        return ResponseEntity.ok().build()
    }
}

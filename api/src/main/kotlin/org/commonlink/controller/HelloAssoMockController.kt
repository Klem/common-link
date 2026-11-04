package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.FakeHelloAssoGateway
import org.commonlink.service.HelloAssoNotificationOutcome
import org.commonlink.service.HelloAssoNotificationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Drives [FakeHelloAssoGateway] in local / E2E mock mode (`app.helloasso.mock=true`), never under `prod` — the bean
 * does not exist otherwise, so these routes answer 404. Simulates what only HelloAsso can do: an organization becoming
 * eligible, a payment changing state, a notification being sent (delivered straight to the notification service,
 * skipping the source-IP check that a local caller cannot pass).
 */
@RestController
@RequestMapping("/api/public/dev/helloasso")
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.helloasso", name = ["mock"], havingValue = "true")
@Tag(
    name = "Dev · HelloAsso mock",
    description = "Local / E2E only (app.helloasso.mock=true, never under prod): simulates HelloAsso-side events " +
        "against the fake HelloAsso gateway. Absent (404) otherwise.",
)
class HelloAssoMockController(
    private val fake: FakeHelloAssoGateway,
    private val notificationService: HelloAssoNotificationService,
) {

    /** Sets the mock `isCashInCompliant` of organization [slug]. */
    @PostMapping("/organizations/{slug}/cash-in")
    @Operation(
        summary = "Mock: set an organization's isCashInCompliant",
        description = "Read by the next eligibility re-read (scheduled, on request, or after an Organization notification).",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Eligibility set", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Mock mode disabled", content = [Content()]),
    )
    fun setCashIn(@PathVariable slug: String, @RequestParam compliant: Boolean): ResponseEntity<Void> {
        fake.setCashIn(slug, compliant)
        return ResponseEntity.noContent().build()
    }

    /** Sets the payment state of mock checkout-intent [id]. */
    @PostMapping("/checkout-intents/{id}/state")
    @Operation(
        summary = "Mock: set a checkout-intent's payment state (Authorized, Refused, Refunded, Contested…)",
        description = "Applied to the donation by its next re-read (notification, return page or reconciler).",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "State set", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Unknown checkout-intent, or mock mode disabled", content = [Content()]),
    )
    fun setState(@PathVariable id: Long, @RequestParam state: String): ResponseEntity<Void> =
        if (fake.setPaymentState(id, state)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    /** Sends HelloAsso's `Order` notification of checkout-intent [checkoutIntentId] to the URL of [webhookToken]. */
    @PostMapping("/notifications/{webhookToken}")
    @Operation(
        summary = "Mock: send HelloAsso's Order notification of a checkout-intent to an association's URL",
        description = "Delivered straight to the notification service (no source-IP check); returns what it turned into.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Notification processed: UNKNOWN_TOKEN, IGNORED, PAYMENT or ORGANIZATION",
            content = [Content(schema = Schema(implementation = HelloAssoNotificationOutcome::class))],
        ),
        ApiResponse(responseCode = "404", description = "Mock mode disabled", content = [Content()]),
    )
    fun notifyOrder(
        @PathVariable webhookToken: String,
        @RequestParam checkoutIntentId: Long,
    ): ResponseEntity<HelloAssoNotificationOutcome> {
        val publicRef = fake.metadataOf(checkoutIntentId)?.get("publicRef")
        val metadata = publicRef?.let { ""","metadata":{"publicRef":"$it"}""" }.orEmpty()
        val body = """{"eventType":"Order","data":{"id":$checkoutIntentId,"checkoutIntentId":$checkoutIntentId}$metadata}"""
        return ResponseEntity.ok(notificationService.receive(webhookToken, body))
    }
}

package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.commonlink.security.ClientIpResolver
import org.commonlink.service.HelloAssoNotificationService
import org.commonlink.service.TechnicalAlertKind
import org.commonlink.service.TechnicalAlertService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Receives HelloAsso notifications, one URL per association (`/api/public/webhooks/helloasso/{token}`, the token
 * shown to the association while it links its account). No signature exists in API keys mode (HMAC is partner-only),
 * so authenticity rests on the **source IP allow-list** and on re-reading every state at HelloAsso — never the body.
 *
 * Answers 200 as fast as possible (HelloAsso resends until it gets one, up to 16 times over ~27 h): processing is
 * asynchronous, and any processing failure is reported, never returned. An unknown token also gets a silent 200, so a
 * stale URL does not trigger 16 retries. The body is never logged.
 */
@RestController
@RequestMapping("/api/public/webhooks")
@Tag(name = "Webhooks")
class HelloAssoWebhookController(
    private val notificationService: HelloAssoNotificationService,
    private val clientIpResolver: ClientIpResolver,
    private val technicalAlertServiceProvider: ObjectProvider<TechnicalAlertService>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/helloasso/{token}")
    @Operation(
        summary = "HelloAsso notification",
        description = "Called by HelloAsso only, from its notification IPs. Filters the organization's events: only " +
            "CommonLink's payments and organization events are processed (asynchronously, by re-reading HelloAsso); " +
            "anything else is ignored without being stored.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Received (processed or ignored)", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Source IP is not a HelloAsso notification address", content = [Content()]),
    )
    fun receive(
        @PathVariable token: String,
        @RequestBody(required = false) body: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val ip = clientIpResolver.resolve(request)
        if (!notificationService.isAllowedSource(ip)) {
            log.warn("HelloAsso notification refused: source {} not allowed", ip)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        try {
            notificationService.receive(token, body.orEmpty())
        } catch (ex: Exception) {
            log.error("HelloAsso notification processing failed: {}", ex.javaClass.simpleName)
            technicalAlertServiceProvider.ifAvailable?.reportFailure(
                TechnicalAlertKind.WEBHOOK_PROCESSING_FAILURE, "POST", "/api/public/webhooks/helloasso", ex,
            )
        }
        return ResponseEntity.ok().build()
    }
}

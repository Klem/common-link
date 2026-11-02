package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.PaymentStatusDto
import org.commonlink.service.AssociationPaymentService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Provider-neutral payment endpoints for the authenticated association. `ROLE_ASSOCIATION` is enforced by the
 * `/api/association` path matcher in [org.commonlink.security.SecurityConfig].
 */
@RestController
@RequestMapping("/api/association/payment")
@Tag(name = "Association · Payment", description = "Payment provider status of the authenticated association")
class AssociationPaymentController(
    private val associationPaymentService: AssociationPaymentService,
) {

    @GetMapping("/status")
    @Operation(
        summary = "Get payment setup status",
        description = "Returns the association's payment provider and its bank-setup status (NOT_CONNECTED, " +
            "NEEDS_DATA, IN_REVIEW, COMPLETED, BROKEN). A stale stored status is first refreshed from the provider " +
            "(at most once every 5 minutes per association); a failed refresh falls back to the stored status. " +
            "A provider with no implementation yet (HELLOASSO before its adapter ships) is returned as NOT_CONNECTED, " +
            "without refresh.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Payment status returned",
            content = [Content(schema = Schema(implementation = PaymentStatusDto::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
    )
    fun getStatus(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<PaymentStatusDto> =
        ResponseEntity.ok(associationPaymentService.getPaymentStatus(UUID.fromString(principal.username)))
}

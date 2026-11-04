package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.ChangePaymentProviderRequest
import org.commonlink.dto.PaymentProviderPreviewDto
import org.commonlink.service.PaymentProviderChangeService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Curator back-office: an association's payment provider, its preview and its change. */
@RestController
@RequestMapping("/api/admin/associations/{associationId}/payment-provider")
@Tag(name = "Admin · Payment provider", description = "CURATOR-only view and change of an association's payment provider.")
@PreAuthorize("hasAnyRole('CURATOR','ADMIN')")
class AdminAssociationPaymentProviderController(
    private val paymentProviderChangeService: PaymentProviderChangeService,
) {

    /** Returns the association's current provider and its readiness with each provider. */
    @GetMapping
    @Operation(
        summary = "Preview the association's payment provider",
        description = "Returns the current provider, the association's readiness with each provider (null = provider " +
            "not available yet), LIVE campaigns, widget state and donations still in flight at the current provider.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Preview returned",
            content = [Content(schema = Schema(implementation = PaymentProviderPreviewDto::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association not found", content = [Content()]),
    )
    fun preview(@PathVariable associationId: UUID): ResponseEntity<PaymentProviderPreviewDto> =
        ResponseEntity.ok(paymentProviderChangeService.preview(associationId))

    /** Changes the association's payment provider (curator decision, reason journaled). */
    @PutMapping
    @Operation(
        summary = "Change the association's payment provider",
        description = "Moves the association's new payments to the given provider, with a mandatory reason (max " +
            "${PaymentProviderChangeService.REASON_MAX_LENGTH} characters). The previous connection is kept and " +
            "in-flight payments stay with their provider. Journaled (PAYMENT_PROVIDER_CHANGED) and emailed to the " +
            "association without the reason. Same provider = no-op. Returns the updated preview.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Provider changed (or unchanged); updated preview returned",
            content = [Content(schema = Schema(implementation = PaymentProviderPreviewDto::class))],
        ),
        ApiResponse(responseCode = "400", description = "Malformed body or unknown provider", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Missing, blank or too long reason", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association not found", content = [Content()]),
    )
    fun change(
        @PathVariable associationId: UUID,
        @Valid @RequestBody request: ChangePaymentProviderRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<PaymentProviderPreviewDto> =
        ResponseEntity.ok(
            paymentProviderChangeService.change(
                associationId, request.provider, request.reason, UUID.fromString(principal.username),
            )
        )
}

package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.CreatePayoutRequest
import org.commonlink.dto.PayoutDto
import org.commonlink.dto.PayoutSummaryDto
import org.commonlink.service.PayoutService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/campaigns/{campaignId}/payments")
@Tag(name = "Payouts", description = "Campaign outgoing payments (payouts)")
class PayoutController(private val payoutService: PayoutService) {

    @PostMapping
    @Operation(summary = "Create a payout", description = "Creates a new PENDING payout for a campaign.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Payout created",
            content = [Content(schema = Schema(implementation = PayoutDto::class))]),
        ApiResponse(responseCode = "400", description = "Validation error", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign, payee, or IBAN not found", content = [Content()]),
    )
    fun create(
        @PathVariable campaignId: UUID,
        @Valid @RequestBody request: CreatePayoutRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<PayoutDto> {
        val result = payoutService.create(campaignId, request, UUID.fromString(principal.username))
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PostMapping("/{payoutId}/confirm")
    @Operation(summary = "Confirm a payout", description = "Transitions a PENDING payout to CONFIRMED and enqueues an on-chain job.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Payout confirmed",
            content = [Content(schema = Schema(implementation = PayoutDto::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Payout not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Payout is not in PENDING status", content = [Content()]),
    )
    fun confirm(
        @PathVariable campaignId: UUID,
        @PathVariable payoutId: UUID,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<PayoutDto> =
        ResponseEntity.ok(payoutService.confirm(campaignId, payoutId, UUID.fromString(principal.username)))

    @GetMapping
    @Operation(summary = "List payouts", description = "Returns a paginated list of payouts for a campaign, newest first.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Payout list returned", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()]),
    )
    fun list(
        @PathVariable campaignId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Page<PayoutDto>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return ResponseEntity.ok(payoutService.list(campaignId, UUID.fromString(principal.username), pageable))
    }

    @GetMapping("/{payoutId}")
    @Operation(summary = "Get a payout", description = "Returns a single payout by ID.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Payout returned",
            content = [Content(schema = Schema(implementation = PayoutDto::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Payout not found", content = [Content()]),
    )
    fun get(
        @PathVariable campaignId: UUID,
        @PathVariable payoutId: UUID,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<PayoutDto> =
        ResponseEntity.ok(payoutService.get(campaignId, payoutId, UUID.fromString(principal.username)))

    @GetMapping("/summary")
    @Operation(summary = "Get payout summary", description = "Returns aggregated KPIs for the Payments tab.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Summary returned",
            content = [Content(schema = Schema(implementation = PayoutSummaryDto::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()]),
    )
    fun summary(
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<PayoutSummaryDto> =
        ResponseEntity.ok(payoutService.getSummary(campaignId, UUID.fromString(principal.username)))
}

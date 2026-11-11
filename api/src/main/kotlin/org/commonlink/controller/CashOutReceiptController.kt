package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.CashOutReceiptDonationDto
import org.commonlink.dto.CashOutReceiptResponse
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.service.CashOutReceiptService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The authenticated association confirms it received a HelloAsso payout on its bank account ("Transfer received").
 * `ROLE_ASSOCIATION` only (CEO decision Q3), enforced by the `/api/association` path matcher in
 * [org.commonlink.security.SecurityConfig]; the payout is always looked up within the caller's association.
 */
@RestController
@RequestMapping("/api/association/cash-outs")
@Tag(name = "Association · HelloAsso payouts", description = "Confirming receipt of HelloAsso payouts")
class CashOutReceiptController(
    private val receiptService: CashOutReceiptService,
    private val associationProfileRepository: AssociationProfileRepository,
) {

    /** Confirms payout [idCashOut] received; see [CashOutReceiptService.confirm]. */
    @PostMapping("/{idCashOut}/receipt")
    @Operation(
        summary = "Confirm a HelloAsso payout was received",
        description = "Makes the association's donations of HelloAsso payout idCashOut available for payments, before " +
            "the settlement delay ends. Each donation is re-read at HelloAsso first and must be paid out in this payout. " +
            "Covers every campaign of the association. Refunded donations are left out. Idempotent: a second call " +
            "changes nothing. Journaled in the compliance log.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donations of the payout available",
            content = [Content(schema = Schema(implementation = CashOutReceiptResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No donation of the association in this payout", content = [Content()]),
        ApiResponse(
            responseCode = "409",
            description = "A donation is not paid out in this payout at HelloAsso (code CASH_OUT_NOT_CASHED_OUT), or no " +
                "active HelloAsso link (code HELLOASSO_NOT_LINKED). Nothing changed.",
            content = [Content()],
        ),
        ApiResponse(responseCode = "502", description = "HelloAsso unavailable; nothing changed", content = [Content()]),
    )
    fun confirmReceipt(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable idCashOut: Long,
    ): ResponseEntity<CashOutReceiptResponse> {
        val userId = UUID.fromString(principal.username)
        val association = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        val result = receiptService.confirm(association.id!!, idCashOut, userId)
        return ResponseEntity.ok(
            CashOutReceiptResponse(
                idCashOut = result.idCashOut,
                netTotal = result.netTotal,
                donations = result.donations.map {
                    CashOutReceiptDonationDto(it.donationId, it.campaignId, it.amount, it.availableAt, it.confirmationMode)
                },
            ),
        )
    }
}

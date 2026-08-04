package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.dto.CreateGuestDonationResponse
import org.commonlink.dto.DonationStatusDto
import org.commonlink.dto.PublicLandingDto
import org.commonlink.dto.PublicWidgetDto
import org.commonlink.service.PublicWidgetService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Public (unauthenticated) widget and landing page endpoints.
 *
 * Accessible without a JWT - whitelisted in [org.commonlink.security.SecurityConfig]
 * under /api/public/. Exposes only donor-safe data; no internal IDs or sensitive fields.
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public Widget", description = "Public donation widget endpoints (no authentication required)")
class PublicWidgetController(
    private val publicWidgetService: PublicWidgetService,
) {

    /**
     * Resolves a widget token to the campaign details needed by the donation iframe.
     *
     * Returns a safe projection of the campaign - no internal IDs, no association contact details.
     *
     * @param widgetToken Opaque public token identifying the association's widget (e.g. clk_...).
     * @return [PublicWidgetDto] with association name, campaign info, and fundraising progress.
     */
    @GetMapping("/widget/{widgetToken}")
    @Operation(
        summary = "Get widget campaign info",
        description = "Resolves a widget token to the campaign details needed for the donation iframe. No authentication required."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Widget resolved - campaign is live and accepting donations",
            content = [Content(schema = Schema(implementation = PublicWidgetDto::class))]
        ),
        ApiResponse(responseCode = "404", description = "Unknown token or no destination campaign configured", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Destination campaign exists but is not LIVE", content = [Content()])
    )
    fun getWidget(@PathVariable widgetToken: String): ResponseEntity<PublicWidgetDto> =
        ResponseEntity.ok(publicWidgetService.getWidget(widgetToken))

    /**
     * Creates a guest donation and a Mollie hosted-checkout payment for the given widget.
     *
     * The donation is persisted in PENDING state. Confirmation happens asynchronously
     * when the Mollie webhook fires (B6). The caller must redirect the donor to [checkoutUrl]
     * and store [paymentId] in sessionStorage before doing so, so the return page can poll status.
     *
     * @param widgetToken Opaque public token identifying the association's widget.
     * @param request Donation body including amount, donor identity and RGPD consent.
     * @return [CreateGuestDonationResponse] with the Mollie checkout URL and payment ID.
     */
    @PostMapping("/widget/{widgetToken}/donations")
    @Operation(
        summary = "Create guest donation",
        description = "Initiates a guest donation for the widget campaign. Returns a Mollie checkout URL. No authentication required."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donation initiated - redirect donor to checkoutUrl",
            content = [Content(schema = Schema(implementation = CreateGuestDonationResponse::class))]
        ),
        ApiResponse(responseCode = "404", description = "Unknown token or no LIVE destination campaign", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Destination campaign is not accepting donations", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation failed (amount, email, identity or consent)", content = [Content()])
    )
    fun createDonation(
        @PathVariable widgetToken: String,
        @Valid @RequestBody request: CreateGuestDonationRequest,
    ): ResponseEntity<CreateGuestDonationResponse> =
        ResponseEntity.ok(publicWidgetService.createDonation(widgetToken, request))

    /**
     * Returns the public confirmation status of a donation identified by its Mollie payment ID.
     *
     * Used by the return page to poll until confirmation or timeout.
     * Returns only PENDING/CONFIRMED — no internal data exposed.
     *
     * @param paymentId Mollie payment ID (tr_…), without the "mollie:" prefix.
     */
    @GetMapping("/widget/donations/{paymentId}/status")
    @Operation(
        summary = "Get donation payment status",
        description = "Returns CONFIRMED when the Mollie webhook has confirmed the payment, PENDING otherwise. No authentication required."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Status returned",
            content = [Content(schema = Schema(implementation = DonationStatusDto::class))]
        ),
        ApiResponse(responseCode = "404", description = "Payment ID not found", content = [Content()])
    )
    fun getDonationStatus(@PathVariable paymentId: String): ResponseEntity<DonationStatusDto> =
        ResponseEntity.ok(publicWidgetService.getDonationStatus(paymentId))

    /**
     * Returns the full landing page data for the association campaign associated with this widget token.
     *
     * Includes association identity, campaign details, expense budget projection with percentages,
     * milestones, and the applicable fiscal tax reduction rate.
     * No internal IDs, contact details, or sensitive fields are exposed.
     *
     * @param widgetToken Opaque public token identifying the association's widget.
     * @param preview Optional preview token issued to the owning association; lifts the LIVE
     *   requirement on the destination campaign, and nothing else. Ignored when it does not belong
     *   to the association owning [widgetToken].
     * @return [PublicLandingDto] with all donor-safe association and campaign data.
     */
    @GetMapping("/landing/{widgetToken}")
    @Operation(
        summary = "Get landing page data",
        description = "Returns full association and campaign data for the landing page. Includes budget projection and milestones. " +
            "No authentication required. A `preview` token issued to the owning association allows rendering a non-LIVE campaign."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Landing data resolved - campaign is live, or a valid preview token was supplied",
            content = [Content(schema = Schema(implementation = PublicLandingDto::class))]
        ),
        ApiResponse(responseCode = "404", description = "Unknown token or no destination campaign configured", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Destination campaign exists but is not LIVE and no valid preview token was supplied", content = [Content()])
    )
    fun getLanding(
        @PathVariable widgetToken: String,
        @RequestParam(required = false) preview: String?,
    ): ResponseEntity<PublicLandingDto> =
        ResponseEntity.ok(publicWidgetService.getLanding(widgetToken, preview))
}

package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.commonlink.dto.CampaignReportRequest
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.dto.CreateGuestDonationResponse
import org.commonlink.dto.DonationStatusDto
import org.commonlink.dto.PublicLandingDto
import org.commonlink.dto.PublicWidgetDto
import org.commonlink.security.AuthRateLimiter
import org.commonlink.security.ClientIpResolver
import org.commonlink.service.CampaignReportService
import org.commonlink.service.PublicWidgetService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

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
    private val campaignReportService: CampaignReportService,
    private val rateLimiter: AuthRateLimiter,
    private val clientIpResolver: ClientIpResolver,
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
        ApiResponse(responseCode = "422", description = "Validation failed (amount, email, identity or consent)", content = [Content()]),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = [Content()])
    )
    fun createDonation(
        @PathVariable widgetToken: String,
        @Valid @RequestBody request: CreateGuestDonationRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<CreateGuestDonationResponse> {
        // Unauthenticated, and every call writes rows, screens a donor and calls Mollie on the
        // association's account. Two independent quotas: one bounds a single caller, the other
        // bounds the damage to one widget when many callers are involved
        // (security audit 2026-08-20, M6).
        rateLimiter.check("donation:ip:${clientIpResolver.resolve(httpRequest)}", maxAttempts = 10, windowMinutes = 10)
        rateLimiter.check("donation:widget:$widgetToken", maxAttempts = 60, windowMinutes = 10)
        // Accidental double-submit guard (double-click, form resubmit) — refuses a byte-identical
        // retry within 60s before it reaches the service. Distinct from the two quotas above,
        // which bound abuse volume rather than catch one caller's literal duplicate; also distinct
        // from Mollie's own Idempotency-Key header, which protects Mollie's ledger, not this side.
        rateLimiter.check(
            "donation:submit:$widgetToken:${request.donorEmail.trim().lowercase()}:${request.amount.toPlainString()}",
            maxAttempts = 1,
            windowMinutes = 1,
        )
        return ResponseEntity.ok(publicWidgetService.createDonation(widgetToken, request))
    }

    /**
     * Returns the public confirmation status of a donation identified by its opaque public ref.
     *
     * Used by the return page to poll until confirmation or timeout.
     * Returns only PENDING/CONFIRMED (+ payment method once confirmed) — no internal data exposed.
     *
     * @param ref Opaque correlation id handed to the donor on the Mollie redirect URL (`ref` query
     *   param) — not the Mollie payment id, not any internal donor/campaign id.
     */
    @GetMapping("/widget/donations/{ref}/status")
    @Operation(
        summary = "Get donation payment status",
        description = "Returns CONFIRMED when the Mollie webhook has confirmed the payment, PENDING otherwise. No authentication required."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Status returned",
            content = [Content(schema = Schema(implementation = DonationStatusDto::class))]
        ),
        ApiResponse(responseCode = "404", description = "Unknown ref", content = [Content()])
    )
    fun getDonationStatus(@PathVariable ref: UUID): ResponseEntity<DonationStatusDto> =
        ResponseEntity.ok(publicWidgetService.getDonationStatus(ref))

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

    /**
     * Reports the widget's destination campaign to the compliance function (IC-44).
     *
     * Opens (or reuses) a `CAMPAIGN_REPORT` compliance alert and raises the owning association to
     * `AssociationStatus.ALERT` — internal only, does not affect public visibility or donations.
     * No authentication required; reporting works with or without a CommonLink account.
     *
     * @param widgetToken Opaque public token identifying the association's widget.
     * @param request Free-text report message and an optional reporter e-mail.
     */
    @PostMapping("/widget/{widgetToken}/report")
    @Operation(
        summary = "Report a campaign",
        description = "Records a compliance report against the widget's destination campaign. No authentication required."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Report recorded"),
        ApiResponse(responseCode = "404", description = "Unknown token or no destination campaign configured", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Validation failed (blank message or invalid e-mail)", content = [Content()]),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = [Content()])
    )
    fun reportCampaign(
        @PathVariable widgetToken: String,
        @Valid @RequestBody request: CampaignReportRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Unit> {
        // Unauthenticated and write-only. ALERT is non-blocking (see AssociationStatus KDoc), so the
        // worst case of abuse is spam to the compliance mailbox, not a takedown — a proportionate
        // throttle, not the tighter donation-path quotas.
        rateLimiter.check("report:ip:${clientIpResolver.resolve(httpRequest)}", maxAttempts = 5, windowMinutes = 10)
        rateLimiter.check("report:widget:$widgetToken", maxAttempts = 20, windowMinutes = 10)
        campaignReportService.report(widgetToken, request.message, request.reporterEmail)
        return ResponseEntity.ok().build()
    }
}

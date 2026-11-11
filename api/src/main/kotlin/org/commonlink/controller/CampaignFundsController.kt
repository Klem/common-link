package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.CampaignFundsResponse
import org.commonlink.service.CampaignFundsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The funds of one of the authenticated association's campaigns (Payments tab, "Où en sont vos dons"), under the
 * association's campaign routes (`/api/association/campaigns`, as the meta-plan's contract). `ROLE_ASSOCIATION`,
 * enforced by the `/api/association` path matcher in [org.commonlink.security.SecurityConfig]; ownership checked by
 * [CampaignFundsService] (another association's campaign is not found).
 */
@RestController
@RequestMapping("/api/association/campaigns/{campaignId}/funds")
@Tag(name = "Association · Campaign funds", description = "Where the campaign's donations stand, and what can be paid with them")
class CampaignFundsController(
    private val fundsService: CampaignFundsService,
) {

    /** The campaign's funds; no side effect. See [CampaignFundsService.getFunds]. */
    @GetMapping
    @Operation(
        summary = "Get the campaign's funds",
        description = "Donations by stage (at HelloAsso, payout in preparation, in transit, arrived, withdrawn), payouts, " +
            "the amount a new payment can use (same computation as the payment creation check), HelloAsso payouts " +
            "carrying the campaign's donations, curator releases, and the state of the HelloAsso tracking. Amounts in euros.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Funds returned",
            content = [Content(schema = Schema(implementation = CampaignFundsResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found for this association", content = [Content()]),
    )
    fun getFunds(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable campaignId: UUID,
    ): ResponseEntity<CampaignFundsResponse> =
        ResponseEntity.ok(fundsService.getFunds(campaignId, UUID.fromString(principal.username)))

    /** Requests a HelloAsso tracking pass, then returns the funds. See [CampaignFundsService.refresh]. */
    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh the campaign's funds",
        description = "Asks HelloAsso where the association's donations stand (opening of the Payments tab, \"Refresh\" " +
            "button), then returns the funds. At most one tracking pass per association every 15 minutes (configurable), " +
            "whatever the campaign or the number of clicks: a request over the limit runs nothing and still answers 200 " +
            "with the known state and nextRefreshAvailableAt. No-op for an association without HelloAsso donation.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Funds returned (refreshed or not)",
            content = [Content(schema = Schema(implementation = CampaignFundsResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found for this association", content = [Content()]),
    )
    fun refresh(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable campaignId: UUID,
    ): ResponseEntity<CampaignFundsResponse> =
        ResponseEntity.ok(fundsService.refresh(campaignId, UUID.fromString(principal.username)))
}

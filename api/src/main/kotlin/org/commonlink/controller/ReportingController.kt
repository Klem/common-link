package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.BudgetVarianceDto
import org.commonlink.service.ReportingService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/campaigns/{campaignId}/reporting")
@Tag(name = "Reporting", description = "Budget variance reporting for campaigns")
class ReportingController(private val reportingService: ReportingService) {

    @GetMapping
    @Operation(
        summary = "Get budget variance",
        description = "Returns planned vs actual budget variance for each section of a campaign. " +
            "Charges: CONFIRMED payouts grouped by typeCode prefix. " +
            "Produits: confirmed donations grouped by typeCode.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Variance report returned",
            content = [Content(schema = Schema(implementation = BudgetVarianceDto::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found or not owned by association", content = [Content()]),
    )
    fun getVariance(
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal principal: UserDetails,
    ): BudgetVarianceDto =
        reportingService.getVariance(campaignId, UUID.fromString(principal.username))
}

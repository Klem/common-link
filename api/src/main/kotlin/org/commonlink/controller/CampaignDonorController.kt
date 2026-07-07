package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.CampaignDonorDto
import org.commonlink.dto.DonationDto
import org.commonlink.service.DonorAggregateService
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/campaigns/{campaignId}")
@Tag(name = "Campaign Donors", description = "Per-campaign donor aggregates and donation history")
class CampaignDonorController(private val donorAggregateService: DonorAggregateService) {

    @GetMapping("/donors")
    @Operation(
        summary = "List donors",
        description = "Returns a paginated list of donor aggregates for a campaign. " +
            "Anonymous donors appear as 'Anonyme' and are excluded from name search.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Donor list returned", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()]),
    )
    fun listDonors(
        @PathVariable campaignId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "amount") sort: String,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Page<CampaignDonorDto>> {
        val associationId = UUID.fromString(principal.username)
        return ResponseEntity.ok(donorAggregateService.listDonors(campaignId, associationId, search, sort, page, size))
    }

    @GetMapping("/donors/{donorId}/donations")
    @Operation(
        summary = "List donations by donor",
        description = "Returns a paginated list of donations made by a specific donor on the campaign, newest first.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Donation list returned", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()]),
    )
    fun listDonorDonations(
        @PathVariable campaignId: UUID,
        @PathVariable donorId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Page<DonationDto>> {
        val associationId = UUID.fromString(principal.username)
        return ResponseEntity.ok(
            donorAggregateService.listDonorDonations(campaignId, donorId, associationId, page, size),
        )
    }

    @GetMapping("/donations/{donationId}")
    @Operation(
        summary = "Get a donation",
        description = "Returns a single donation scoped to the campaign.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Donation returned", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign or donation not found", content = [Content()]),
    )
    fun getDonation(
        @PathVariable campaignId: UUID,
        @PathVariable donationId: UUID,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<DonationDto> {
        val associationId = UUID.fromString(principal.username)
        return ResponseEntity.ok(donorAggregateService.getDonation(campaignId, donationId, associationId))
    }
}

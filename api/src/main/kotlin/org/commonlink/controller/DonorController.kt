package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.DonorAssociationDto
import org.commonlink.dto.DonorCampaignReportDto
import org.commonlink.dto.DonorDonationDto
import org.commonlink.dto.DonorDonationFiltersDto
import org.commonlink.dto.DonorDonationJourneyDto
import org.commonlink.dto.DonorImpactDto
import org.commonlink.dto.DonorProfileDto
import org.commonlink.dto.DonorReceiptYearDto
import org.commonlink.dto.DonorStatsDto
import org.commonlink.dto.PageResponse
import org.commonlink.dto.UpdateDonorProfileRequest
import org.commonlink.dto.toPageResponse
import org.commonlink.service.CampaignReportPdfService
import org.commonlink.service.DonorAssociationService
import org.commonlink.service.DonorCampaignReportService
import org.commonlink.service.DonorDashboardService
import org.commonlink.service.DonorDonationJourneyService
import org.commonlink.service.DonorImpactService
import org.commonlink.service.DonorReceiptsService
import org.commonlink.service.DonorService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/donor")
@Tag(name = "Donor", description = "Donor profile and dashboard endpoints")
class DonorController(
    private val donorService: DonorService,
    private val donorDashboardService: DonorDashboardService,
    private val donorAssociationService: DonorAssociationService,
    private val donorDonationJourneyService: DonorDonationJourneyService,
    private val donorCampaignReportService: DonorCampaignReportService,
    private val campaignReportPdfService: CampaignReportPdfService,
    private val donorReceiptsService: DonorReceiptsService,
    private val donorImpactService: DonorImpactService,
) {

    @GetMapping("/me")
    @Operation(
        summary = "Get donor profile",
        description = "Returns the donor profile for the authenticated user."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donor profile returned",
            content = [Content(schema = Schema(implementation = DonorProfileDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun getProfile(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<DonorProfileDto> =
        ResponseEntity.ok(donorService.getProfile(UUID.fromString(principal.username)))

    @PatchMapping("/me")
    @Operation(
        summary = "Update donor profile",
        description = "Updates the donor profile for the authenticated user. Only provided fields are updated."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donor profile updated",
            content = [Content(schema = Schema(implementation = DonorProfileDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun updateProfile(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: UpdateDonorProfileRequest
    ): ResponseEntity<DonorProfileDto> =
        ResponseEntity.ok(donorService.updateProfile(UUID.fromString(principal.username), req))

    @GetMapping("/me/donations")
    @Operation(
        summary = "List the donor's donations",
        description = "Confirmed donations of the authenticated donor, newest first. " +
            "Both filters are optional: omit them to get the whole history."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donation page returned",
            content = [Content(schema = Schema(implementation = PageResponse::class))]
        ),
        ApiResponse(responseCode = "400", description = "Paging or year out of range", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun listDonations(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) associationId: UUID?,
        @RequestParam(required = false) year: Int?,
    ): ResponseEntity<PageResponse<DonorDonationDto>> =
        ResponseEntity.ok(
            donorDashboardService
                .listDonations(UUID.fromString(principal.username), associationId, year, page, size)
                .toPageResponse()
        )

    @GetMapping("/me/donations/filters")
    @Operation(
        summary = "Values available in the donation history filters",
        description = "Associations funded by the authenticated donor and years with at least one confirmed donation."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Filter values returned",
            content = [Content(schema = Schema(implementation = DonorDonationFiltersDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun getDonationFilters(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<DonorDonationFiltersDto> =
        ResponseEntity.ok(donorDashboardService.getFilters(UUID.fromString(principal.username)))

    @GetMapping("/me/stats")
    @Operation(
        summary = "Donor headline figures",
        description = "Total donated, number of donations, associations supported, and the " +
            "**estimated** tax reduction — an estimate, never a guaranteed amount."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Stats returned",
            content = [Content(schema = Schema(implementation = DonorStatsDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun getStats(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<DonorStatsDto> =
        ResponseEntity.ok(donorDashboardService.getStats(UUID.fromString(principal.username)))

    @GetMapping("/me/donations/{donationId}/receipt", produces = [MediaType.APPLICATION_PDF_VALUE])
    @Operation(
        summary = "Download the fiscal receipt of a donation",
        description = "Returns the stored Cerfa PDF. 403 when the donation belongs to another donor, " +
            "404 when it does not exist or no receipt has been generated for it."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Receipt PDF returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Donation belongs to another donor", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donation or receipt not found", content = [Content()])
    )
    fun downloadReceipt(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable donationId: UUID,
    ): ResponseEntity<ByteArray> {
        val receipt = donorDashboardService.getReceipt(UUID.fromString(principal.username), donationId)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${receipt.fileName}\"")
            .body(receipt.pdfBytes)
    }

    @GetMapping("/me/donations/{donationId}/journey")
    @Operation(
        summary = "Get the traceability journey of a donation",
        description = "Derives the 4-step journey (received, recorded on-chain, spent, impact reported) " +
            "of one of the authenticated donor's donations, plus précédent/suivant navigation."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Journey returned",
            content = [Content(schema = Schema(implementation = DonorDonationJourneyDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Donation belongs to another donor", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donation not found", content = [Content()])
    )
    fun getDonationJourney(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable donationId: UUID,
    ): ResponseEntity<DonorDonationJourneyDto> =
        ResponseEntity.ok(donorDonationJourneyService.getJourney(UUID.fromString(principal.username), donationId))

    @GetMapping("/me/campaigns/{campaignId}/report")
    @Operation(
        summary = "Get the donor-facing campaign report",
        description = "Hero data, the donor's own contribution, milestones, confirmed payouts and " +
            "budget variance for a campaign the authenticated donor has funded."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Report returned",
            content = [Content(schema = Schema(implementation = DonorCampaignReportDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Donor has no confirmed donation on this campaign", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()])
    )
    fun getCampaignReport(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable campaignId: UUID,
    ): ResponseEntity<DonorCampaignReportDto> =
        ResponseEntity.ok(donorCampaignReportService.getReport(UUID.fromString(principal.username), campaignId))

    @GetMapping("/me/campaigns/{campaignId}/report/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    @Operation(
        summary = "Download the donor-facing campaign report as a PDF",
        description = "Same content as GET .../report, rendered as a downloadable PDF."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Report PDF returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Donor has no confirmed donation on this campaign", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()])
    )
    fun downloadCampaignReportPdf(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable campaignId: UUID,
    ): ResponseEntity<ByteArray> {
        val report = donorCampaignReportService.getReport(UUID.fromString(principal.username), campaignId)
        val pdfBytes = campaignReportPdfService.generate(report)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bilan-${campaignId}.pdf\"")
            .body(pdfBytes)
    }

    @GetMapping("/me/associations")
    @Operation(
        summary = "List the associations the donor supports",
        description = "One entry per association funded by the authenticated donor, most funded first."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Associations returned",
            content = [Content(schema = Schema(implementation = DonorAssociationDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun listAssociations(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<List<DonorAssociationDto>> =
        ResponseEntity.ok(donorAssociationService.listAssociations(UUID.fromString(principal.username)))

    @GetMapping("/me/receipts")
    @Operation(
        summary = "Annual fiscal recap summaries",
        description = "One row per calendar year with at least one receipted donation."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Yearly summaries returned",
            content = [Content(schema = Schema(implementation = DonorReceiptYearDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun getReceiptYears(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<List<DonorReceiptYearDto>> =
        ResponseEntity.ok(donorReceiptsService.getYearlySummaries(UUID.fromString(principal.username)))

    @GetMapping("/me/receipts/{year}/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    @Operation(
        summary = "Download the annual fiscal recap as a PDF",
        description = "One line per receipted donation of that calendar year, with the tax reduction rate applied."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Recap PDF returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found, or no receipted donation that year", content = [Content()])
    )
    fun downloadAnnualReceiptsSummary(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable year: Int,
    ): ResponseEntity<ByteArray> {
        val pdfBytes = donorReceiptsService.getAnnualSummaryPdf(UUID.fromString(principal.username), year)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"recapitulatif-fiscal-$year.pdf\"")
            .body(pdfBytes)
    }

    @GetMapping("/me/impacts")
    @Operation(
        summary = "Impact gallery of the donor's funded campaigns",
        description = "One card per campaign the donor has funded, never a per-donor share of the impact (D6)."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Impacts returned",
            content = [Content(schema = Schema(implementation = DonorImpactDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Donor profile not found", content = [Content()])
    )
    fun getImpacts(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<List<DonorImpactDto>> =
        ResponseEntity.ok(donorImpactService.listImpacts(UUID.fromString(principal.username)))
}

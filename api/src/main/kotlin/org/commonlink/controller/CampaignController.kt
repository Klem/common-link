package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.CampaignDto
import org.commonlink.dto.CampaignSummaryDto
import org.commonlink.dto.CreateCampaignRequest
import org.commonlink.dto.CreateMilestoneRequest
import org.commonlink.dto.MilestoneDto
import org.commonlink.dto.ReorderMilestonesRequest
import org.commonlink.dto.SaveBudgetRequest
import org.commonlink.dto.UpdateCampaignRequest
import org.commonlink.dto.UpdateMilestoneRequest
import org.commonlink.service.CampaignService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/association/campaigns")
@Tag(name = "Campaign", description = "Campaign management for associations")
class CampaignController(
    private val campaignService: CampaignService
) {

    /**
     * Returns all campaigns for the authenticated association, sorted by creation date descending.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @return 200 with the list of campaign summaries (may be empty).
     */
    @GetMapping
    @Operation(
        summary = "List campaigns",
        description = "Returns all campaigns for the authenticated association."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "List of campaigns returned",
            content = [Content(schema = Schema(implementation = Array<CampaignSummaryDto>::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun listCampaigns(
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<List<CampaignSummaryDto>> =
        ResponseEntity.ok(campaignService.listCampaigns(UUID.fromString(principal.username)))

    /**
     * Creates a new campaign under the authenticated association.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param req Validated creation request.
     * @return 201 with the created campaign DTO.
     */
    @PostMapping
    @Operation(
        summary = "Create campaign",
        description = "Creates a new campaign under the authenticated association."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "Campaign created",
            content = [Content(schema = Schema(implementation = CampaignDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error (e.g. blank name)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun createCampaign(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: CreateCampaignRequest
    ): ResponseEntity<CampaignDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            campaignService.createCampaign(UUID.fromString(principal.username), req)
        )

    /**
     * Returns full campaign detail including budget sections (with items) and milestones.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the campaign to retrieve.
     * @return 200 with the full campaign DTO.
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get campaign detail",
        description = "Returns campaign with budget sections and milestones."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Campaign returned",
            content = [Content(schema = Schema(implementation = CampaignDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()])
    )
    fun getCampaign(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<CampaignDto> =
        ResponseEntity.ok(campaignService.getCampaign(UUID.fromString(principal.username), id))

    /**
     * Partially updates a campaign — only non-null fields in the request are applied.
     *
     * Status transitions are validated: DRAFT→LIVE and LIVE→ENDED are allowed;
     * backwards transitions (e.g. ENDED→LIVE) are rejected with 422.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the campaign to update.
     * @param req Partial update request; null fields are ignored.
     * @return 200 with the updated campaign DTO.
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Update campaign",
        description = "Partially updates a campaign. Only non-null fields are applied. Status transitions DRAFT→LIVE and LIVE→ENDED are allowed."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Campaign updated",
            content = [Content(schema = Schema(implementation = CampaignDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Invalid status transition", content = [Content()])
    )
    fun updateCampaign(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateCampaignRequest
    ): ResponseEntity<CampaignDto> =
        ResponseEntity.ok(campaignService.updateCampaign(UUID.fromString(principal.username), id, req))

    /**
     * Deletes a campaign and all its related data (budget sections, items, milestones via cascade).
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the campaign to delete.
     * @return 204 No Content on success.
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete campaign",
        description = "Deletes a campaign and all its related budget sections, items, and milestones."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Campaign deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()])
    )
    fun deleteCampaign(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        campaignService.deleteCampaign(UUID.fromString(principal.username), id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Replaces the entire budget structure for a campaign in a single atomic operation.
     *
     * All existing sections and items are deleted before the new structure is persisted.
     * Sending an empty sections list clears the budget entirely.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the campaign whose budget is being replaced.
     * @param req New budget structure (sections → items).
     * @return 200 with the updated campaign DTO including the new budget.
     */
    @PutMapping("/{id}/budget")
    @Operation(
        summary = "Save budget",
        description = "Replaces the entire budget structure for the campaign. Sending an empty sections list clears the budget."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Budget saved",
            content = [Content(schema = Schema(implementation = CampaignDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()])
    )
    fun saveBudget(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: SaveBudgetRequest
    ): ResponseEntity<CampaignDto> =
        ResponseEntity.ok(campaignService.saveBudget(UUID.fromString(principal.username), id, req))

    /**
     * Adds a new milestone to a campaign.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the campaign to add the milestone to.
     * @param req Milestone creation request.
     * @return 201 with the created milestone DTO.
     */
    @PostMapping("/{id}/milestones")
    @Operation(
        summary = "Add milestone",
        description = "Adds a new milestone to the campaign."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "Milestone created",
            content = [Content(schema = Schema(implementation = MilestoneDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error (e.g. blank title)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign not found", content = [Content()])
    )
    fun addMilestone(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: CreateMilestoneRequest
    ): ResponseEntity<MilestoneDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            campaignService.addMilestone(UUID.fromString(principal.username), id, req)
        )

    /**
     * Partially updates a milestone — only non-null fields in the request are applied.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the campaign that owns the milestone.
     * @param msId UUID of the milestone to update.
     * @param req Partial update request; null fields are ignored.
     * @return 200 with the updated milestone DTO.
     */
    @PutMapping("/{id}/milestones/{msId}")
    @Operation(
        summary = "Update milestone",
        description = "Partially updates a milestone. Only non-null fields are applied."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Milestone updated",
            content = [Content(schema = Schema(implementation = MilestoneDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign or milestone not found", content = [Content()])
    )
    fun updateMilestone(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @PathVariable msId: UUID,
        @Valid @RequestBody req: UpdateMilestoneRequest
    ): ResponseEntity<MilestoneDto> =
        ResponseEntity.ok(campaignService.updateMilestone(UUID.fromString(principal.username), id, msId, req))

    /**
     * Deletes a milestone from a campaign.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the campaign that owns the milestone.
     * @param msId UUID of the milestone to delete.
     * @return 204 No Content on success.
     */
    @DeleteMapping("/{id}/milestones/{msId}")
    @Operation(
        summary = "Delete milestone",
        description = "Deletes a milestone from the campaign."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Milestone deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign or milestone not found", content = [Content()])
    )
    fun deleteMilestone(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @PathVariable msId: UUID
    ): ResponseEntity<Void> {
        campaignService.deleteMilestone(UUID.fromString(principal.username), id, msId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Reorders milestones for a campaign by reassigning sort order based on the position
     * of each milestone ID in the request list (first ID gets sortOrder=0, etc.).
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the campaign whose milestones are being reordered.
     * @param req Ordered list of milestone UUIDs.
     * @return 200 with the updated list of milestone DTOs in the new order.
     */
    @PutMapping("/{id}/milestones/reorder")
    @Operation(
        summary = "Reorder milestones",
        description = "Reorders milestones for a campaign. The first ID in the list gets sortOrder=0, the second gets sortOrder=1, and so on."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Milestones reordered",
            content = [Content(schema = Schema(implementation = Array<MilestoneDto>::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Campaign or milestone not found", content = [Content()])
    )
    fun reorderMilestones(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: ReorderMilestonesRequest
    ): ResponseEntity<List<MilestoneDto>> =
        ResponseEntity.ok(campaignService.reorderMilestones(UUID.fromString(principal.username), id, req))
}

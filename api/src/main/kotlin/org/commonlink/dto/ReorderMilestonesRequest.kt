package org.commonlink.dto

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

/**
 * Request body for reordering all milestones of a campaign.
 *
 * The [milestoneIds] list defines the new display order: the first UUID gets sortOrder 0,
 * the second gets sortOrder 1, and so on. All milestone IDs belonging to the campaign
 * must be included.
 *
 * @param milestoneIds Ordered list of milestone UUIDs defining the new sort order.
 */
data class ReorderMilestonesRequest(
    @field:NotEmpty
    val milestoneIds: List<UUID>
)

package org.commonlink.dto

import org.commonlink.entity.CampaignMilestone
import org.commonlink.entity.MilestoneStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Response DTO for a campaign milestone.
 *
 * @param id UUID of the milestone.
 * @param emoji Visual icon emoji for the milestone.
 * @param title Short title describing what will be achieved.
 * @param description Optional detailed description of the milestone deliverable.
 * @param targetAmount Amount of donations required to reach this milestone.
 * @param status Current progress status (LOCKED, CURRENT, or REACHED).
 * @param sortOrder Display position in the milestone list; lower value = shown first.
 * @param reachedAt Timestamp when the milestone was reached. Null until status is REACHED.
 * @param createdAt Timestamp of record creation.
 */
data class MilestoneDto(
    val id: UUID,
    val emoji: String,
    val title: String,
    val description: String?,
    val targetAmount: BigDecimal,
    val status: MilestoneStatus,
    val sortOrder: Int,
    val reachedAt: Instant?,
    val createdAt: Instant
)

/**
 * Converts a [CampaignMilestone] entity to a [MilestoneDto].
 */
fun CampaignMilestone.toDto() = MilestoneDto(
    id = id!!,
    emoji = emoji,
    title = title,
    description = description,
    targetAmount = targetAmount,
    status = status,
    sortOrder = sortOrder,
    reachedAt = reachedAt,
    createdAt = createdAt
)

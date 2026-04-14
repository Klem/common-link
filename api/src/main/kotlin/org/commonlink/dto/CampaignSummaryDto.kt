package org.commonlink.dto

import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Lightweight response DTO for campaign list views.
 *
 * Does not include budget sections or milestone details — only summary-level data
 * sufficient for displaying a campaign card or row in a list.
 *
 * @param id UUID of the campaign.
 * @param name Display name of the campaign.
 * @param emoji Visual icon emoji.
 * @param description Optional detailed description.
 * @param goal Total fundraising goal in euros.
 * @param raised Amount raised so far in euros.
 * @param status Current lifecycle status.
 * @param startDate Optional start date for donation acceptance.
 * @param endDate Optional end date for donation acceptance.
 * @param milestoneCount Total number of milestones defined for this campaign.
 * @param createdAt Timestamp of record creation.
 */
data class CampaignSummaryDto(
    val id: UUID,
    val name: String,
    val emoji: String,
    val description: String?,
    val goal: BigDecimal,
    val raised: BigDecimal,
    val status: CampaignStatus,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val milestoneCount: Int,
    val createdAt: Instant
)

/**
 * Converts a [Campaign] entity to a [CampaignSummaryDto] without loading budget sections.
 */
fun Campaign.toSummaryDto() = CampaignSummaryDto(
    id = id!!,
    name = name,
    emoji = emoji,
    description = description,
    goal = goal,
    raised = raised,
    status = status,
    startDate = startDate,
    endDate = endDate,
    milestoneCount = milestones.size,
    createdAt = createdAt
)

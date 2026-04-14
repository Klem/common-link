package org.commonlink.dto

import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Full response DTO for a campaign, including its budget sections and milestones.
 *
 * Used in detail views and after create/update operations where the complete state is needed.
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
 * @param contractAddress On-chain contract address, null before deployment.
 * @param budgetSections Hierarchical budget (sections → items).
 * @param milestones Ordered list of campaign milestones.
 * @param createdAt Timestamp of record creation.
 * @param updatedAt Timestamp of last modification.
 */
data class CampaignDto(
    val id: UUID,
    val name: String,
    val emoji: String,
    val description: String?,
    val goal: BigDecimal,
    val raised: BigDecimal,
    val status: CampaignStatus,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val contractAddress: String?,
    val budgetSections: List<BudgetSectionDto>,
    val milestones: List<MilestoneDto>,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Converts a [Campaign] entity to a [CampaignDto], eagerly mapping budget sections and milestones.
 */
fun Campaign.toDto() = CampaignDto(
    id = id!!,
    name = name,
    emoji = emoji,
    description = description,
    goal = goal,
    raised = raised,
    status = status,
    startDate = startDate,
    endDate = endDate,
    contractAddress = contractAddress,
    budgetSections = budgetSections.map { it.toDto() },
    milestones = milestones.sortedBy { it.sortOrder }.map { it.toDto() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

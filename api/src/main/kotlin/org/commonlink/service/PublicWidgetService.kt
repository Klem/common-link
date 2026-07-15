package org.commonlink.service

import org.commonlink.dto.PublicWidgetDto
import org.commonlink.entity.CampaignStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Resolves a public widget token to a safe campaign projection for the donation iframe.
 *
 * Resolution rules:
 * - Unknown token → 404
 * - Token found but no destination campaign configured → 404
 * - Destination campaign exists but not LIVE → 409 (campaign not accepting donations)
 * - Otherwise → [PublicWidgetDto] with only donor-safe fields
 */
@Service
class PublicWidgetService(
    private val associationProfileRepository: AssociationProfileRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getWidget(widgetToken: String): PublicWidgetDto {
        val association = associationProfileRepository.findByWidgetToken(widgetToken)
            .orElseThrow { NotFoundException("Widget not found") }

        val campaign = association.widgetDestinationCampaign
            ?: throw NotFoundException("No destination campaign configured")

        if (campaign.status != CampaignStatus.LIVE) {
            logger.debug("Widget token {} resolved to campaign {} with status {}", widgetToken, campaign.id, campaign.status)
            throw ConflictException("Campaign is not accepting donations")
        }

        return PublicWidgetDto(
            associationName = association.name,
            campaignId = campaign.id!!,
            campaignName = campaign.name,
            campaignEmoji = campaign.emoji,
            campaignDescription = campaign.description,
            goal = campaign.goal,
            raised = campaign.raised,
            campaignCoverImage = campaign.coverImage,
        )
    }
}

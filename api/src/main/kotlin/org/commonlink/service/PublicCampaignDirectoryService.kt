package org.commonlink.service

import org.commonlink.dto.PublicCampaignListItemDto
import org.commonlink.repository.CampaignRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Serves the public directory of live campaigns rendered by the landing page (`/projets`).
 *
 * Kept out of [PublicWidgetService] on purpose: that class resolves a *single* widget token and
 * orchestrates donations, while this one only reads a bounded list. The shared eligibility rule
 * lives in the query itself — see
 * [org.commonlink.repository.CampaignRepository.findPublicLive].
 */
@Service
class PublicCampaignDirectoryService(
    private val campaignRepository: CampaignRepository,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns at most [MAX_CAMPAIGNS] publicly listable campaigns, newest first.
     *
     * The cap is not a paging parameter: the endpoint is unauthenticated and hit on every render of
     * the projects page, so the response size must not grow with the number of associations.
     * Pagination is a separate decision to take when the directory actually outgrows one page.
     */
    @Transactional(readOnly = true)
    fun listLive(): List<PublicCampaignListItemDto> {
        val rows = campaignRepository.findPublicLive(PageRequest.of(0, MAX_CAMPAIGNS))
        logger.debug("Public campaign directory served {} campaign(s)", rows.size)
        return rows.map { row ->
            PublicCampaignListItemDto(
                campaignId = row.campaignId,
                campaignName = row.campaignName,
                campaignEmoji = row.campaignEmoji,
                campaignCategory = row.campaignCategory,
                coverImage = row.coverImage,
                campaignUpdatedAt = row.campaignUpdatedAt,
                goal = row.goal,
                raised = row.raised,
                milestoneCount = row.milestoneCount,
                associationName = row.associationName,
                associationLogo = row.associationLogo,
                donationUrl = donationUrl(row.widgetToken),
            )
        }
    }

    /**
     * Builds the absolute donation landing URL for [widgetToken].
     *
     * Uses the configured `app.frontend-url` rather than a hard-coded host so the directory points
     * at the same origin as every other outbound link the backend emits (Mollie Connect returns,
     * transactional emails). The locale segment is `fr`: the landing page is French-only.
     */
    private fun donationUrl(widgetToken: String): String =
        "${frontendUrl.trimEnd('/')}/fr/lp/$widgetToken"

    private companion object {
        /** Hard cap on the number of campaigns returned by the public directory. */
        const val MAX_CAMPAIGNS = 60
    }
}

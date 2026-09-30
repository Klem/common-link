package org.commonlink.repository

import org.commonlink.entity.CampaignStory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CampaignStoryRepository : JpaRepository<CampaignStory, UUID> {

    /** Returns the story of [campaignId], or null if the association never wrote one. */
    fun findByCampaignId(campaignId: UUID): CampaignStory?
}

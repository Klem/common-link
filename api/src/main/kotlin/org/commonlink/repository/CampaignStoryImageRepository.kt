package org.commonlink.repository

import org.commonlink.entity.CampaignStoryImage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CampaignStoryImageRepository : JpaRepository<CampaignStoryImage, UUID>

package org.commonlink.repository

import org.commonlink.entity.CampaignCoverImage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Persistence for campaign cover image binaries, keyed by campaign id. */
interface CampaignCoverImageRepository : JpaRepository<CampaignCoverImage, UUID>

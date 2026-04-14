package org.commonlink.repository

import org.commonlink.entity.CampaignBudgetItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CampaignBudgetItemRepository : JpaRepository<CampaignBudgetItem, UUID>
